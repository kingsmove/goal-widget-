# 미국 여행 앱 (us-trip 브랜치)

UFP(main 브랜치)와 별개인 여행 앱. 일정 · 장소(맛집/숙소) · 지구본 지도 · 경비 · 사진 · 예약.

- 화면: `app/src/main/assets` (HTML/JS, 지도는 MapLibre GL + OpenFreeMap 타일)
- 이 브랜치에 push하면 GitHub Actions가 APK를 빌드해 `trip-N` 릴리스로 올림
- 데이터는 폰 안에만 저장됨 (이 저장소에 올라가지 않음)
