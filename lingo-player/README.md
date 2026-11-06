# Lingo Player — 언어학습 음악 플레이어

기기에 저장된 음악/강의 오디오 파일을 재생하면서 **단어 단위로 가사처럼 싱크된 텍스트**를 보여주는 앱입니다.
Factory Talk와는 별개의 앱이며, 이 폴더 하나로 완결됩니다.

## 기능
- 음악 파일 추가(MP3/M4A/WAV 등, 25MB 이하) → 기기 안 IndexedDB에 저장
- **분석**: Whisper API(단어 타임스탬프)로 문장·단어 타이밍 추출, 결과는 기기에 저장되어 한 번만 분석
  - 클라우드: **Groq**(기본, 무료 티어, `whisper-large-v3` + `llama-3.3-70b-versatile`) 또는 **OpenAI**(유료, `whisper-1` + `gpt-4o-mini`)
  - **기기 내(오프라인)**: transformers.js + ONNX Whisper(`_timestamped` 모델, 단어 타임스탬프 지원)를 폰에서 직접 실행. WebGPU가 되면 GPU, 아니면 CPU(WASM). 모델은 최초 1회 다운로드 후 브라우저 캐시에 저장.
    - large-v3-turbo(최고 정확도, GPU 약 1.6GB / CPU 약 1.1GB), medium, small(약 250MB), base(약 80MB)
    - 번역·단어 뜻은 기기 내 모드에서도 클라우드 키가 있을 때만 동작 (없으면 가사 싱크만)
  - 단어 힌트: 교재 단어·이름을 넣으면 클라우드 인식 시 철자 보정(`prompt`)
- 재생 중 현재 단어 노란색 하이라이트, 현재 문장 강조 + 자동 스크롤
- 문장 탭 → 그 문장부터 재생, 단어 탭 → 뜻/발음 팝업(캐시됨) 및 "여기부터 재생"
- 이전/다음 문장, 5초 이동, 문장 반복, 재생 속도(0.5~1.25x), 번역 표시 토글, 글자 크기
- 문장별 번역(한국어/영어/베트남어/중국어/일본어 선택)

## 잠금화면·백그라운드 재생
- 안드로이드 앱은 `@jofr/capacitor-media-session` 플러그인으로 재생 중 미디어 알림을 띄우고 포그라운드 서비스로 화면이 꺼져도 재생을 유지합니다.
- 잠금화면/알림에는 **현재 문장**이 제목으로, 그 번역이 아티스트 자리에 표시되며 문장이 바뀔 때마다 갱신됩니다.
- 컨트롤: 재생/일시정지, 이전·다음(= 이전·다음 문장), 탐색바, 이어폰 버튼. 웹/PWA에서는 브라우저의 Media Session API를 그대로 씁니다.

## API 키
설정(⚙)에서 제공자(Groq/OpenAI)를 고르고 그 서비스의 API 키를 입력합니다. 키는 기기(localStorage)에만 저장됩니다.
- 내장 키: GitHub 저장소 Settings → Secrets and variables → Actions 에 `GROQ_API_KEY`를 등록하면 빌드 시 APK에 내장되어 입력 없이 바로 씁니다. 설정에서 키를 입력하면 그 키가 우선합니다.
- 주의: 이 저장소는 공개이므로 릴리스 APK도 누구나 받을 수 있고, APK 안의 내장 키는 추출될 수 있습니다. 키를 소스 코드에 직접 쓰지 마세요.
- Groq: console.groq.com 가입 → API Keys. 카드 없이 무료 (하루 약 28,800초 오디오, 분당 20요청, 25MB 제한). 분석·번역·단어 뜻 모두 Groq 키 하나로 동작.
- OpenAI: 선결제 필요. Whisper 분당 약 $0.006, 번역·단어 뜻은 gpt-4o-mini.

## 파일
- `index.html` 앱 본체, `stt-worker.js` 기기 내 Whisper 워커(모듈 워커, CDN에서 transformers.js 로드)

## 빌드
- 웹/PWA: 이 폴더를 HTTPS로 서빙하면 바로 동작 (`python3 -m http.server`로 로컬 테스트 가능)
- Android APK: `.github/workflows/lingo-player.yml` — `lingo-player/` 변경을 main에 푸시하거나 수동 실행하면 `LingoPlayer.apk`가 Release(`lingo-v1.0.N`)에 올라갑니다.

## 주의
- 유튜브에서 오디오를 받는 기능은 앱에 넣지 않았습니다(유튜브 약관/스토어 정책). 파일은 사용자가 직접 준비해 추가합니다.
- 반주가 큰 노래는 인식률이 떨어질 수 있습니다. 보컬이 또렷한 학습용 음원에 가장 잘 맞습니다.
