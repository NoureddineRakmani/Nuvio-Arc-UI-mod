#!/usr/bin/env python3
"""Nuvio Mod: gives the app its own name and icons at build time, without touching Arc's files.

    brand.py <arc folder> <mod folder> --name "PG Prime" [--app-name "PG Prime Test"] [--no-updates]

Used by both builds (mod/dev/try.sh on the PC, .github/workflows/build-my-nuvio.yml for releases).
It writes <arc>/mod-brand/res, a small extra resource folder that the build puts on top of Arc's:
  - every text that names the app, in every language, with the name replaced (rules below);
  - the icons and banners kept in <mod>/brand/res.
Nothing under <arc>/app/src changes, so the name and icons are not part of mod/patches and an Arc
update that edits its texts cannot clash with them.
"""
import argparse
import pathlib
import re
import shutil
import sys

# --- Rules: which texts keep a real name -------------------------------------------------------
# Credits and licence: never renamed at all.
KEEP_WHOLE = {
    "licenses_attributions_nuvio_title",
    "licenses_attributions_nuvio_body",
    "about_made_with_love",
}
# "Nuvio" here is the Nuvio service, account or team, not this app: only "Nuvio Arc"/"Arc" are renamed.
KEEP_NUVIO = re.compile(
    r"^(auth_notice_nuvio_logged_out|account_sign_in_description"
    r"|support_nuvio_.*|about_supporters_contributors|supporters?_.*|sponsors_.*"
    r"|party_nuvio_.*|party_kind_nuvio|party_look_profile_title|party_look_tab_nuvio"
    r"|profile_custom_avatar_web_panel_note|profile_background_member_note"
    r"|profile_local_avatar_note|profile_pin_overlay_forgot_hint"
    r"|backup_.*|servers_account_.*|servers_section_account)$"
)
# Everything else: "Nuvio Arc", "Arc" and "Nuvio" all mean this app and become the new name.
# ------------------------------------------------------------------------------------------------

NOT_BEFORE = r"(?<![A-Za-z0-9_./@])"
ARC = re.compile(NOT_BEFORE + r"(?:Nuvio[ -])?Arc(?![A-Za-z0-9_])")
NUVIO = re.compile(NOT_BEFORE + r"Nuvio(?! ?TV|Media|[A-Za-z0-9_/])")
NUVIO_CAPS = re.compile(NOT_BEFORE + r"NUVIO(?![A-Za-z0-9_/])")
FR_ELISION = re.compile(r"(?<![A-Za-z])(d|D|qu|Qu)(?:\\'|’|')(?=(?:Nuvio )?Arc(?![A-Za-z0-9_]))")
EN_ARTICLE = re.compile(r"\b([Aa])n (?=(?:Nuvio )?Arc(?![A-Za-z0-9_]))")
ELEMENT = re.compile(r"<(string|plurals|string-array)\b([^>]*?)(?<!/)>(.*?)</\1>", re.S)
COMMENT = re.compile(r"<!--.*?-->", re.S)
NAME = re.compile(r'\bname="([^"]+)"')
XMLNS = re.compile(r'\bxmlns:(\w+)="([^"]+)"')


def rename(value, res_name, name, lang):
    if res_name in KEEP_WHOLE:
        return value
    if lang == "fr":  # d'Arc -> de <name>
        value = FR_ELISION.sub(lambda m: m.group(1) + "e ", value)
    elif lang == "":  # an Arc update -> a <name> update
        value = EN_ARTICLE.sub(lambda m: m.group(1) + " ", value)
    value = ARC.sub(name, value)
    if not KEEP_NUVIO.match(res_name):
        value = NUVIO.sub(name, value)
        value = NUVIO_CAPS.sub(name.upper(), value)
    return value


def branded_elements(text, name, app_name, lang):
    """The elements of one Arc text file whose text names the app, renamed."""
    out = []
    for m in ELEMENT.finditer(COMMENT.sub("", text)):
        tag, attrs, value = m.groups()
        res = NAME.search(attrs)
        if not res:
            continue
        new = app_name if (tag == "string" and res.group(1) == "app_name") else rename(value, res.group(1), name, lang)
        if new != value:
            out.append((res.group(1), f"<{tag}{attrs}>{new}</{tag}>"))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("arc")
    ap.add_argument("mod")
    ap.add_argument("--name", required=True, help="the app's name inside its texts")
    ap.add_argument("--app-name", help="the name under the icon (default: --name)")
    ap.add_argument("--no-updates", action="store_true", help="test builds: switch the in-app updater off")
    a = ap.parse_args()
    arc, mod = pathlib.Path(a.arc), pathlib.Path(a.mod)
    app_name = a.app_name or a.name
    src = arc / "app/src/main/res"
    out = arc / "mod-brand/res"
    if out.parent.exists():
        shutil.rmtree(out.parent)

    # 1. Texts.
    texts = files = 0
    app_names = 0
    for d in sorted(p for p in src.glob("values*") if p.is_dir()):
        lang = d.name[len("values"):].lstrip("-").split("-")[0].replace("b+", "").split("+")[0]
        elements, spaces = [], {}
        for f in sorted(d.glob("*.xml")):
            text = f.read_text(encoding="utf-8")
            found = branded_elements(text, a.name, app_name, lang)
            if found:
                spaces.update(XMLNS.findall(text))
                elements += found
        if not elements:
            continue
        app_names += sum(1 for n, _ in elements if n == "app_name")
        ns = "".join(f' xmlns:{k}="{v}"' for k, v in sorted(spaces.items()))
        (out / d.name).mkdir(parents=True, exist_ok=True)
        (out / d.name / "mod_brand.xml").write_text(
            f'<?xml version="1.0" encoding="utf-8"?>\n<resources{ns}>\n'
            + "".join(f"    {e}\n" for _, e in elements) + "</resources>\n", encoding="utf-8")
        texts += len(elements)
        files += 1
    if not app_names:
        sys.exit("brand.py: could not find the app name in Arc's texts.")

    # 2. Icons and banners.
    art = mod / "brand/res"
    images = 0
    missing = []
    for f in sorted(p for p in art.rglob("*") if p.is_file()):
        rel = f.relative_to(art)
        if not (src / rel).exists():
            missing.append(str(rel))
        (out / rel).parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(f, out / rel)
        images += 1
    ours = {p.relative_to(art).as_posix() for p in art.rglob("*") if p.is_file()}
    new_in_arc = sorted(
        p.relative_to(src).as_posix() for p in src.rglob("*")
        if p.is_file() and re.match(r"(ic_launcher|banner|arc_launcher_foreground|app_logo_wordmark)", p.name)
        and p.suffix in (".png", ".webp") and p.relative_to(src).as_posix() not in ours)

    # 3. Tell the build about the extra folder (highest priority: above Arc's own and its debug texts).
    gradle = arc / "app/build.gradle.kts"
    g = gradle.read_text()
    marker = "// Nuvio Mod: name and icons (mod/dev/brand.py)"
    if marker not in g:
        g += (f"\n{marker}\n"
              'listOf("fullDebug", "fullRelease").forEach {\n'
              '    android.sourceSets.maybeCreate(it).res.srcDir(rootProject.file("mod-brand/res"))\n'
              "}\n")
    if a.no_updates:
        g, n = re.subn(r'buildConfigField\("boolean", "UPDATE_CHECK_ENABLED", "true"\)',
                       'buildConfigField("boolean", "UPDATE_CHECK_ENABLED", "false")', g)
    if "nuvioAppIdSuffix" not in g:
        sys.exit("Arc's build file changed: the package suffix option is gone, refusing to build.")
    gradle.write_text(g)

    print(f'Name "{a.name}" (icon label "{app_name}"): {texts} texts in {files} languages, {images} pictures.')
    if missing:
        print(f"  note: {len(missing)} of my pictures no longer exist in Arc (unused now), e.g. {missing[0]}")
    if new_in_arc:
        print(f"  note: Arc has {len(new_in_arc)} icon/banner pictures I have no version of, e.g. {new_in_arc[0]}")


if __name__ == "__main__":
    main()
