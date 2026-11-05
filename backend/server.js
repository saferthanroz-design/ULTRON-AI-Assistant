import 'dotenv/config';
import express from 'express';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';

const app = express();
app.disable('x-powered-by');
app.use(helmet());
app.use(express.json({ limit: '8mb' }));
app.use('/api', rateLimit({ windowMs: 60_000, limit: 20, standardHeaders: 'draft-7', legacyHeaders: false }));

app.get('/health', (_req, res) => res.json({ ok: true, app: 'ULTRON' }));
app.post('/api/chat', async (req, res) => {
  if (process.env.APP_TOKEN && req.get('X-App-Token') !== process.env.APP_TOKEN) {
    return res.status(401).json({ error: 'Unauthorized' });
  }
  const { messages, web = false } = req.body ?? {};
  if (!Array.isArray(messages) || messages.length === 0 || messages.length > 16 || typeof web !== 'boolean') {
    return res.status(400).json({ error: 'Invalid messages or web flag' });
  }
  if (!process.env.OPENAI_API_KEY) return res.status(503).json({ error: 'Configure OPENAI_API_KEY in backend/.env' });
  try {
    const input = messages.map(m => {
      if (!['user', 'assistant'].includes(m.role) || typeof m.content !== 'string' || m.content.length > 12000) throw new Error('Invalid message');
      const content = [{ type: 'input_text', text: m.content }];
      if (m.image) {
        if (m.role !== 'user' || typeof m.image !== 'string' || m.image.length > 6_000_000 || !/^[A-Za-z0-9+/=]+$/.test(m.image)) throw new Error('Invalid image');
        content.push({ type: 'input_image', image_url: `data:image/jpeg;base64,${m.image}` });
      }
      return { role: m.role, content: m.role === 'assistant' ? m.content : content };
    });
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 75_000);
    let response;
    try {
      response = await fetch('https://api.openai.com/v1/responses', {
        method: 'POST', signal: controller.signal,
        headers: { 'Authorization': `Bearer ${process.env.OPENAI_API_KEY}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ model: process.env.OPENAI_MODEL || 'gpt-4.1-mini', instructions: 'You are ULTRON, a helpful, multilingual Android AI assistant. Respond clearly and truthfully. When web search is enabled, cite sources in your response where available. Never claim to have executed actions on apps or devices.', input, ...(web ? { tools: [{ type: 'web_search_preview' }] } : {}) })
      });
    } finally { clearTimeout(timeout); }
    const data = await response.json();
    if (!response.ok) return res.status(502).json({ error: data.error?.message || 'AI provider request failed' });
    const reply = (data.output || []).flatMap(item => item.content || []).filter(c => c.type === 'output_text').map(c => c.text).join('\n').trim();
    return res.json({ reply: reply || 'No text response received.' });
  } catch (error) {
    const invalid = error.message?.startsWith('Invalid');
    return res.status(invalid ? 400 : 502).json({ error: invalid ? error.message : 'Unable to complete AI request' });
  }
});
app.listen(Number(process.env.PORT) || 3000, '0.0.0.0', () => console.log('ULTRON backend listening'));
