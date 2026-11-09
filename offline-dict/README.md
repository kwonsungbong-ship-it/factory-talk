# 오프라인 사전 (Offline Dictionary)

영어 · 베트남어 · 한국어 · 중국어 오프라인 사전 안드로이드 앱. 인터넷, 광고, 서버 확인이 없습니다.

| 사전 | 출처 |
|---|---|
| 영→베, 베→영, 한→베, 베→한, 중→베 | OVDP / Free Vietnamese Dictionary Project ([dynamotn/stardict-vi](https://github.com/dynamotn/stardict-vi)) |
| 중→영 | CC-CEDICT (MDBG, CC BY-SA 4.0) |
| 한국어기초사전 (한→베·영·일·인니), 베→한 기초사전 | 국립국어원 한국어기초사전 (CC BY-SA 2.0 KR, [changwoo/krdict-converted](https://github.com/changwoo/krdict-converted)) |
| 한→영 | [cc-kedict](https://github.com/mhagiwara/cc-kedict) (CC BY-SA) |
| 영→한, 영→중, 중→한·한→중(한자어) | 위 사전에서 자동 생성한 보조 사전 |

빌드: `.github/workflows/offline-dict.yml` 이 사전 데이터를 내려받아 `tools/build_db.py` 로 SQLite DB를 만든 뒤 APK를 빌드하고 `dict-v1.0.N` 릴리스로 올립니다.
