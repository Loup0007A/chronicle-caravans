#!/usr/bin/env python3
"""Fabrique les sources d'une cible (version de Minecraft + loader) à partir des sources communes.

Les sources de référence (src/main) sont celles de NeoForge 1.21.1 : elles compilent telles quelles.
Pour une autre cible, ce script :
  1. copie src/main/java et src/main/resources dans targets/<cible>/build/ported/ ;
  2. évalue les blocs conditionnels  //#if <expr> … //#elif … //#else … //#endif
     (les lignes inactives sont mises en commentaire avec « //$$ », les lignes actives décommentées) ;
  3. applique les remplacements textuels de targets/<cible>/target.json (imports, noms d'API…) ;
  4. recouvre le tout avec targets/<cible>/overrides/ (fichiers entièrement propres à la cible)
     et supprime les fichiers listés dans « delete ».

Expressions : étiquettes de la cible (ex. forge, neoforge, mc120, mc1201, mc121, mc1211, mc26) combinées avec
!, && et ||.  Exemple :  //#if neoforge || mc1201

Usage : python3 tools/port.py <cible>            (ex. forge-1.20.1)
        python3 tools/port.py --check            (vérifie que src/main est bien la variante neoforge-1.21.1)
"""
import json
import os
import re
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'src', 'main')
TEXT_EXT = ('.java',)
# Ressources JSON / TOML : blocs //#if non utilisés (les formats différents passent par overrides/)
# Shaders : blocs //#if traités aussi (sans les remplacements de target.json)
SHADER_EXT = ('.fsh', '.vsh', '.glsl')
DIRECTIVE = re.compile(r'^(\s*)//#(if|elif|else|endif)\b\s*(.*)$')
COMMENTED = re.compile(r'^(\s*)//\$\$ ?(.*)$')

# La variante écrite dans src/main
CANONICAL_TAGS = {'neoforge', 'mc121', 'mc1211'}


def evaluate(expr, tags):
    expr = expr.strip()
    if not expr:
        raise ValueError('condition vide')
    py = expr.replace('&&', ' and ').replace('||', ' or ')
    py = re.sub(r'!(?!=)', ' not ', py)
    names = set(re.findall(r'[A-Za-z_][A-Za-z0-9_]*', py)) - {'and', 'or', 'not'}
    env = {n: (n in tags) for n in names}
    return bool(eval(py, {'__builtins__': {}}, env))


def process(text, tags, path):
    out = []
    stack = []  # [active_now, already_taken, parent_active]
    for n, line in enumerate(text.split('\n'), 1):
        m = DIRECTIVE.match(line)
        if m:
            kind, expr = m.group(2), m.group(3)
            if kind == 'if':
                parent = all(s[0] for s in stack) if stack else True
                cond = evaluate(expr, tags)
                stack.append([cond, cond, parent])
            elif kind == 'elif':
                if not stack:
                    raise SyntaxError(f'{path}:{n}: #elif sans #if')
                s = stack[-1]
                cond = (not s[1]) and evaluate(expr, tags)
                s[0] = cond
                s[1] = s[1] or cond
            elif kind == 'else':
                if not stack:
                    raise SyntaxError(f'{path}:{n}: #else sans #if')
                s = stack[-1]
                s[0] = not s[1]
                s[1] = True
            else:
                if not stack:
                    raise SyntaxError(f'{path}:{n}: #endif sans #if')
                stack.pop()
            out.append(line)
            continue
        if not stack:
            out.append(line)
            continue
        active = all(s[0] for s in stack)
        c = COMMENTED.match(line)
        if active:
            out.append(c.group(1) + c.group(2) if c else line)
        else:
            if c or not line.strip():
                out.append(line)
            else:
                indent = re.match(r'^\s*', line).group(0)
                out.append(indent + '//$$ ' + line[len(indent):])
    if stack:
        raise SyntaxError(f'{path}: #if non fermé')
    return '\n'.join(out)


def rewrite(text, rules):
    for rule in rules:
        a, b = rule[0], rule[1]
        if a.startswith('re:'):
            text = re.sub(a[3:], b, text, flags=re.M)
        else:
            text = text.replace(a, b)
    return text


def check():
    """src/main doit être exactement la variante neoforge-1.21.1 (blocs bien commentés)."""
    bad = 0
    for base, _, files in [*os.walk(os.path.join(SRC, 'java')), *os.walk(os.path.join(SRC, 'resources'))]:
        for f in files:
            if not f.endswith(TEXT_EXT + SHADER_EXT):
                continue
            p = os.path.join(base, f)
            text = open(p, encoding='utf-8').read()
            if process(text, CANONICAL_TAGS, p) != text:
                print('à normaliser :', os.path.relpath(p, ROOT))
                bad += 1
    print('ok' if not bad else f'{bad} fichier(s) incohérent(s)')
    return bad == 0


def port(target):
    tdir = os.path.join(ROOT, 'targets', target)
    conf = json.load(open(os.path.join(tdir, 'target.json'), encoding='utf-8'))
    tags = set(conf['tags'])
    rules = conf.get('rewrites', [])
    shader_rules = conf.get('shader_rewrites', [])
    out = os.path.join(tdir, 'build', 'ported')
    if os.path.exists(out):
        shutil.rmtree(out)
    count = 0
    for kind in ('java', 'resources'):
        src_root = os.path.join(SRC, kind)
        for base, _, files in os.walk(src_root):
            for f in files:
                sp = os.path.join(base, f)
                rel = os.path.relpath(sp, src_root)
                dp = os.path.join(out, kind, rel)
                os.makedirs(os.path.dirname(dp), exist_ok=True)
                if f.endswith(TEXT_EXT):
                    text = open(sp, encoding='utf-8').read()
                    text = rewrite(process(text, tags, sp), rules)
                    open(dp, 'w', encoding='utf-8').write(text)
                    count += 1
                elif f.endswith(SHADER_EXT):
                    text = open(sp, encoding='utf-8').read()
                    text = rewrite(process(text, tags, sp), shader_rules)
                    # pas de commentaire avant #version : on retire directives et lignes inactives
                    text = '\n'.join(l for l in text.split('\n') if not re.match(r'^\s*//(#|\$\$)', l))
                    open(dp, 'w', encoding='utf-8').write(text)
                else:
                    shutil.copy2(sp, dp)
    over = os.path.join(tdir, 'overrides')
    if os.path.isdir(over):
        for base, _, files in os.walk(over):
            for f in files:
                sp = os.path.join(base, f)
                rel = os.path.relpath(sp, over)
                dp = os.path.join(out, rel)
                os.makedirs(os.path.dirname(dp), exist_ok=True)
                shutil.copy2(sp, dp)
    for rel in conf.get('delete', []):
        p = os.path.join(out, rel)
        if os.path.isdir(p):
            shutil.rmtree(p)
        elif os.path.exists(p):
            os.remove(p)
    print(f'{target} : {count} fichiers Java portés -> {os.path.relpath(out, ROOT)}')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    if sys.argv[1] == '--check':
        sys.exit(0 if check() else 1)
    port(sys.argv[1])
