// 기기 내(오프라인) Whisper 음성인식 워커.
// transformers.js로 ONNX Whisper 모델을 내려받아(최초 1회, 브라우저 캐시) 단어 타임스탬프를 뽑는다.
import { pipeline, env } from 'https://cdn.jsdelivr.net/npm/@huggingface/transformers@3.8.1/dist/transformers.min.js';

env.allowLocalModels = false;

let pipe = null, loaded = '';

function post(m) { self.postMessage(m); }

async function load(model, device, dtype) {
  const k = model + '|' + device + '|' + JSON.stringify(dtype);
  if (pipe && loaded === k) return pipe;
  if (pipe) { try { await pipe.dispose(); } catch (e) {} pipe = null; }
  const files = {};
  pipe = await pipeline('automatic-speech-recognition', model, {
    device, dtype,
    progress_callback: p => {
      if (p.status === 'progress' && p.file) {
        files[p.file] = [p.loaded || 0, p.total || 0];
        let l = 0, t = 0; for (const f in files) { l += files[f][0]; t += files[f][1]; }
        post({ type: 'progress', loaded: l, total: t, file: p.file });
      } else if (p.status === 'ready') post({ type: 'ready' });
    }
  });
  loaded = k;
  return pipe;
}

self.onmessage = async e => {
  const { id, model, device, dtype, audio, language } = e.data;
  try {
    const p = await load(model, device, dtype); // GPU 실패 시 메인 스레드가 워커를 새로 띄워 CPU로 재시도
    post({ type: 'status', id, text: '음성 인식 중…' });
    const en = /\.en$/.test(model); // 영어 전용 모델은 language/task 지정 불가
    const out = await p(audio, {
      ...(en ? {} : { language: language || null, task: 'transcribe' }),
      return_timestamps: 'word',
      chunk_length_s: 30,
      stride_length_s: 5,
      force_full_sequences: false
    });
    const words = (out.chunks || []).map(c => ({ word: c.text, start: c.timestamp[0], end: c.timestamp[1] == null ? c.timestamp[0] + 0.5 : c.timestamp[1] }));
    post({ type: 'done', id, text: out.text || '', words });
  } catch (err) {
    post({ type: 'error', id, message: (err && err.message) || String(err) });
  }
};
