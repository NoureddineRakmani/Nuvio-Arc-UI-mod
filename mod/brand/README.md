# Name and icons

`res/` holds my icons and TV banners (same folder and file names as Arc's `app/src/main/res`).
`mod/dev/brand.py` puts them, and the app's name in every text, on top of Arc at build time.
They are not part of `mod/patches`.

- Change a picture: replace the file here (same name and size).
- Change which texts keep the name "Nuvio" (credits, licence, Nuvio account, supporting the Nuvio team):
  the two lists at the top of `mod/dev/brand.py`.
- The names themselves: `APP_LABEL` in `.github/workflows/build-my-nuvio.yml` (releases) and
  `NAME` / `LABEL` in `mod/dev/try.sh` (test app).
