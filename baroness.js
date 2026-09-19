// api/baroness.js
module.exports = async function handler(req, res) {
  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed' });
  }

  // Parse body safely for JSON or raw string
  const body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body;
  const {
    text,
    provider = 'deepgram',
    voice = 'aura-asteria-en'
  } = body;

  if (!text) {
    return res.status(400).json({ error: 'Missing text field' });
  }

  const deepgramKey = process.env.DEEPGRAM_API_KEY;
  const murfKey = process.env.MURF_API_KEY;

  // ---------- 1. PRIMARY: Deepgram Aura TTS ----------
  if (provider === 'deepgram' && deepgramKey) {
    try {
      // Direct use of aura-asteria-en as the validated default
      const dgVoice = voice.startsWith('aura-') ? voice : "aura-asteria-en";

      const dgResponse = await fetch(`https://api.deepgram.com/v1/speak?model=${dgVoice}`, {
        method: 'POST',
        headers: {
          'Authorization': `Token ${deepgramKey}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ text: text })
      });

      if (dgResponse.ok) {
        const audioBuffer = await dgResponse.arrayBuffer();
        res.setHeader('Content-Type', 'audio/mpeg');
        return res.send(Buffer.from(audioBuffer));
      } else {
        const errorData = await dgResponse.json();
        console.error(`Deepgram Error (${dgResponse.status}):`, JSON.stringify(errorData));
      }
    } catch (err) {
      console.error('Deepgram Exception:', err.message);
    }
  }

  // ---------- 2. SECONDARY: Murf AI ----------
  if (murfKey) {
    try {
      const murfVoice = voice.includes('en-US') ? voice : "en-US-marcus";

      const murfResponse = await fetch('https://api.murf.ai/v1/speech/generate', {
        method: 'POST',
        headers: {
          'api-key': murfKey,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          text: text,
          voiceId: murfVoice,
          modelVersion: "GEN2",
          style: 'conversational',
          rate: 0,
          pitch: 0,
          format: 'MP3',
          channelType: 'MONO',
          encodeAsBase64: false,
        }),
      });

      if (murfResponse.ok) {
        const murfData = await murfResponse.json();
        const audioResponse = await fetch(murfData.audioFile);
        const audioBuffer = await audioResponse.arrayBuffer();
        res.setHeader('Content-Type', 'audio/mpeg');
        return res.send(Buffer.from(audioBuffer));
      } else {
        const errorText = await murfResponse.text();
        console.error(`Murf AI error (${murfResponse.status}):`, errorText);
      }
    } catch (err) {
      console.error('Murf AI exception:', err.message);
    }
  }

  // ---------- 3. FINAL FALLBACK: Edge TTS ----------
  try {
    const { synthesize: edgeTTS } = await import('@echristian/edge-tts');
    const edgeResult = await edgeTTS({
      text: text,
      voice: 'en-US-JennyNeural',
      outputFormat: 'audio-24khz-96kbitrate-mono-mp3',
    });

    let audioBuffer = Buffer.isBuffer(edgeResult.audio)
      ? edgeResult.audio
      : Buffer.from(edgeResult.audio);

    res.setHeader('Content-Type', 'audio/mpeg');
    return res.send(audioBuffer);
  } catch (err) {
    console.error('Edge TTS fallback failed:', err.message);
    return res.status(500).json({ error: 'All TTS engines failed, mate' });
  }
}
