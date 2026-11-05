
import 'dotenv/config';
import express from 'express';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';

const app = express();

app.disable('x-powered-by');
app.use(helmet());
app.use(express.json({ limit: '8mb' }));

app.use('/api', rateLimit({
  windowMs: 60_000,
  limit: 20,
  standardHeaders: 'draft-7',
  legacyHeaders: false
}));

app.get('/health', (_req, res) => {
  res.json({
    ok: true,
    app: 'ULTRON',
    provider: 'Gemini'
  });
});

app.post('/api/chat', async (req, res) => {
  if (
    process.env.APP_TOKEN &&
    req.get('X-App-Token') !== process.env.APP_TOKEN
  ) {
    return res.status(401).json({
      error: 'Unauthorized'
    });
  }

  const { messages, web = false } = req.body ?? {};

  if (
    !Array.isArray(messages) ||
    messages.length === 0 ||
    messages.length > 16 ||
    typeof web !== 'boolean'
  ) {
    return res.status(400).json({
      error: 'Invalid messages or web flag'
    });
  }

  const apiKey = process.env.GEMINI_API_KEY;
  const model = process.env.GEMINI_MODEL || 'gemini-2.5-flash';

  if (!apiKey) {
    return res.status(503).json({
      error: 'GEMINI_API_KEY is not configured'
    });
  }

  try {
    const contents = messages.map(m => {
      if (
        !m ||
        !['user', 'assistant'].includes(m.role) ||
        typeof m.content !== 'string' ||
        m.content.length > 12000
      ) {
        throw new Error('Invalid message');
      }

      const parts = [];

      if (m.content.trim()) {
        parts.push({ text: m.content });
      }

      if (m.image != null) {
        if (
          m.role !== 'user' ||
          typeof m.image !== 'string' ||
          m.image.length > 6000000 ||
          !/^[A-Za-z0-9+/]+={0,2}$/.test(m.image)
        ) {
          throw new Error('Invalid image');
        }

        parts.push({
          inlineData: {
            mimeType: 'image/jpeg',
            data: m.image
          }
        });
      }

      if (parts.length === 0) {
        throw new Error('Invalid message');
      }

      return {
        role: m.role === 'assistant' ? 'model' : 'user',
        parts
      };
    });

    const payload = {
      systemInstruction: {
        parts: [{
          text: `You are ULTRON, a helpful multilingual
Android AI assistant.

You can communicate in English, Hindi,
Telugu and other supported languages.

Be accurate, friendly and helpful.
Never claim to have performed device actions
that you have not actually performed.

When using web research, cite sources
when available.`
        }]
      },
      contents,
      generationConfig: {
        temperature: 0.7,
        maxOutputTokens: 2048
      }
    };

    if (web) {
      payload.tools = [{
        googleSearch: {}
      }];
    }

    const controller = new AbortController();
    const timeout = setTimeout(
      () => controller.abort(),
      75000
    );

    let response;

    try {
      response = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`,
        {
          method: 'POST',
          signal: controller.signal,
          headers: {
            'Content-Type': 'application/json',
            'x-goog-api-key': apiKey
          },
          body: JSON.stringify(payload)
        }
      );
    } finally {
      clearTimeout(timeout);
    }

    const data = await response.json();

    if (!response.ok) {
      console.error(
        'Gemini API error:',
        response.status,
        data.error?.message
      );

      return res.status(502).json({
        error: data.error?.message ||
          'Gemini API request failed'
      });
    }

    const candidate = data.candidates?.[0];

    const reply = (candidate?.content?.parts || [])
      .filter(part => typeof part.text === 'string')
      .map(part => part.text)
      .join('\n')
      .trim();

    if (!reply) {
      return res.status(502).json({
        error: candidate?.finishReason
          ? `No response: ${candidate.finishReason}`
          : 'No text response received'
      });
    }

    const sources = (
      candidate?.groundingMetadata?.groundingChunks || []
    )
      .map(chunk => chunk.web)
      .filter(source => source?.uri)
      .map(source => ({
        title: source.title || 'Source',
        url: source.uri
      }));

    return res.json({
      reply,
      provider: 'gemini',
      model,
      sources
    });

  } catch (error) {
    console.error('ULTRON error:', error.message);

    const invalid =
      error.message?.startsWith('Invalid');

    return res.status(invalid ? 400 : 502).json({
      error: invalid
        ? error.message
        : error.name === 'AbortError'
          ? 'AI request timed out'
          : 'Unable to complete Gemini request'
    });
  }
});

const PORT = Number(process.env.PORT) || 3000;

app.listen(PORT, '0.0.0.0', () => {
  console.log(`ULTRON Gemini backend running on ${PORT}`);
});
