"""Tool 모듈 7종.

실연결:
  - player_database      (data/players.json 로더)
  - catalog_analyzer     (data/catalog.json gap 분석)
  - color_extractor      (Pillow + sklearn K-means)
  - pack_writer_tool     (팩 디렉토리·manifest 저장)

Dual-path (키 있으면 실호출, 없으면 stub):
  - web_search           (OpenAI Responses web_search_preview)
  - image_generate       (OpenAI gpt-image-1-mini 기본, dall-e-3 하위 호환)
  - face_logo_detector   (OpenAI GPT-4o-mini Vision)
"""
