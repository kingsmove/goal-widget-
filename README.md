# MDD (mdd 브랜치)

개인용 일기 앱. 기분·점수·태그 기록, 달력, 통계, 보관함, 과거의 나에게 답장, 다시 보기, 월간 회고, PIN·생체 잠금.

- 화면: `app/src/main/assets/index.html` (단일 HTML — PC 브라우저에서도 그대로 열림)
- 네이티브 브릿지: 생체 인증(BiometricPrompt), 다시 보기 알림(AlarmManager), 사진 선택
- 이 브랜치에 push하면 GitHub Actions가 APK를 빌드해 `mdd-N` 릴리스로 올림
- 일기 데이터는 기기 안(IndexedDB)에만 저장되고 이 저장소에는 올라가지 않음
