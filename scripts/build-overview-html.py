"""从 project_overall.md 生成带侧边栏导航的单文件 HTML。

用法:
    python scripts/build-overview-html.py [源 md] [输出 html]
默认:
    C:/Users/31139/Desktop/project_overall.md
    C:/Users/31139/Desktop/project_overall.html

依赖: 已装入隔离环境 default venv 的 markdown 库。
"""
import io, re, html, json, sys

SRC = sys.argv[1] if len(sys.argv) > 1 else r'C:/Users/31139/Desktop/project_overall.md'
OUT = sys.argv[2] if len(sys.argv) > 2 else r'C:/Users/31139/Desktop/project_overall.html'

raw = io.open(SRC, encoding='utf-8').read()

# 1) 去掉 md 内置目录块与「返回目录」行（HTML 版有侧边栏，这些是冗余）
lines = raw.split('\n')
kept, skip = [], False
for l in lines:
    if l.strip() == '## 目录':
        skip = True
        continue
    if skip:
        if l.startswith('## '):
            skip = False
        else:
            continue
    if l.strip() == '[↑ 返回目录](#目录)':
        continue
    kept.append(l)
md = '\n'.join(kept)

# 让 md_in_html 解析 <details> 内部的 Markdown（只在 HTML 版加，不动 md 源文件）
md = md.replace('<details>', '<details markdown="1">')

import markdown
from pygments.formatters import HtmlFormatter

body = markdown.markdown(
    md,
    extensions=['tables', 'fenced_code', 'codehilite', 'md_in_html',
                'attr_list', 'sane_lists'],
    extension_configs={
        'codehilite': {
            'guess_lang': False,       # 只对显式标注了语言的块高亮
            'css_class': 'highlight',
            'use_pygments': True,
            'noclasses': False,        # 用 class 而不是内联样式，便于深浅色切换
            'linenos': False,
        }
    },
    output_format='html5',
)

# 两套代码高亮配色：浅色用 friendly，深色用 github-dark
pyg_light = HtmlFormatter(style='friendly').get_style_defs('.highlight')
pyg_dark = HtmlFormatter(style='github-dark').get_style_defs('.highlight')

def slug(text):
    s = html.unescape(re.sub(r'<[^>]+>', '', text))
    s = s.replace('`', '').replace('**', '')
    s = s.lower()
    s = ''.join(ch for ch in s if ch.isalnum() or ch in ' -_')
    return s.replace(' ', '-')

# 2) 给 h2/h3 注入 id，并收集侧边栏条目
heads = []
counter = [0]

def add_id(m):
    lvl, inner = m.group(1), m.group(2)
    sl = slug(inner)
    if lvl in ('2', '3'):
        counter[0] += 1
        heads.append({'lvl': int(lvl), 'id': sl, 'text': re.sub(r'<[^>]+>', '', inner)})
    return f'<h{lvl} id="{sl}">{inner}</h{lvl}>'

body = re.sub(r'<h([23])>(.*?)</h\1>', add_id, body, flags=re.S)

# 3) 侧边栏 HTML
nav = []
for h in heads:
    cls = 'lvl2' if h['lvl'] == 2 else 'lvl3'
    nav.append(f'<a class="nav-item {cls}" href="#{h["id"]}" data-id="{h["id"]}">'
               f'<span class="caret"></span><span class="txt">{html.escape(h["text"])}</span></a>')
nav_html = '\n'.join(nav)

# 4) 组装单文件 HTML
page = '''<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Deep Explore 项目总览</title>
<style>
:root{
  --bg:#ffffff; --bg-side:#fafaf8; --bg-soft:#f5f5f2; --bg-code:#f6f6f4;
  --fg:#1f1f1f; --fg-2:#5c5c5c; --fg-3:#8a8a8a;
  --line:#e6e6e1; --line-2:#d8d8d2;
  --accent:#2f6fed; --accent-soft:#eaf1fd;
  --radius:8px; --side-w:300px;
  --mono:"SFMono-Regular",Consolas,"Liberation Mono",Menlo,"Courier New",monospace;
}
@media (prefers-color-scheme: dark){
  :root{
    --bg:#17181a; --bg-side:#1c1d1f; --bg-soft:#202124; --bg-code:#202124;
    --fg:#e8e8e6; --fg-2:#a8a8a4; --fg-3:#7a7a76;
    --line:#2e2f31; --line-2:#3a3b3e;
    --accent:#7ba7ff; --accent-soft:#22314f;
  }
}
*{box-sizing:border-box}
html{scroll-behavior:smooth; scroll-padding-top:24px}
body{
  margin:0; background:var(--bg); color:var(--fg);
  font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif;
  font-size:15px; line-height:1.75; -webkit-font-smoothing:antialiased;
}
#progress{position:fixed;top:0;left:0;height:2px;width:0;background:var(--accent);z-index:60;transition:width .1s linear}

/* ---------- 侧边栏 ---------- */
#side{
  position:fixed;top:0;left:0;width:var(--side-w);height:100vh;
  background:var(--bg-side);border-right:1px solid var(--line);
  display:flex;flex-direction:column;z-index:50;
}
#side .hd{
  padding:16px 18px 10px;border-bottom:1px solid var(--line);flex:0 0 auto;
}
#side .hd .t{font-size:14px;font-weight:600;letter-spacing:.2px}
#side .hd .s{font-size:12px;color:var(--fg-3);margin-top:2px}
#side .hd .btns{margin-top:10px;display:flex;gap:6px;flex-wrap:wrap}
#side .hd button{
  font:inherit;font-size:12px;padding:3px 9px;border:1px solid var(--line-2);
  background:var(--bg);color:var(--fg-2);border-radius:999px;cursor:pointer;
}
#side .hd button:hover{border-color:var(--accent);color:var(--accent)}
#nav{overflow-y:auto;flex:1 1 auto;padding:8px 10px 40px}
#nav::-webkit-scrollbar{width:8px}
#nav::-webkit-scrollbar-thumb{background:var(--line-2);border-radius:4px}
.nav-item{
  display:flex;align-items:flex-start;gap:4px;
  padding:5px 10px;margin:1px 0;border-radius:6px;
  color:var(--fg-2);text-decoration:none;font-size:13px;line-height:1.5;
  border-left:2px solid transparent;
}
.nav-item:hover{background:var(--bg-soft);color:var(--fg)}
.nav-item.active{background:var(--accent-soft);color:var(--accent);border-left-color:var(--accent);font-weight:500}
.nav-item.lvl3{padding-left:24px;font-size:12.5px;color:var(--fg-3)}
.nav-item.lvl3.active{color:var(--accent)}
.nav-item .caret{
  flex:0 0 auto;width:10px;height:10px;margin-top:5px;position:relative;cursor:pointer;
}
.nav-item .caret::before{
  content:"";position:absolute;left:1px;top:2px;
  border:4px solid transparent;border-left-color:var(--fg-3);
  transition:transform .15s;
}
.nav-item.collapsed .caret::before{transform:rotate(-90deg);transform-origin:2px 4px}
.nav-item .txt{flex:1 1 auto}

/* ---------- 正文 ---------- */
#main{margin-left:var(--side-w);padding:0 40px 160px}
#doc{max-width:900px;margin:0 auto;padding-top:52px}
#doc h1{font-size:28px;font-weight:600;margin:0 0 6px;letter-spacing:-.2px}
#doc h2{
  font-size:21px;font-weight:600;margin:56px 0 14px;padding-bottom:8px;
  border-bottom:1px solid var(--line);
}
#doc h3{font-size:16.5px;font-weight:600;margin:34px 0 10px}
#doc h4{font-size:15px;font-weight:600;margin:24px 0 8px;color:var(--fg-2)}
#doc p{margin:10px 0}
#doc a{color:var(--accent);text-decoration:none}
#doc a:hover{text-decoration:underline}
#doc strong{font-weight:600}
#doc ul,#doc ol{margin:10px 0;padding-left:24px}
#doc li{margin:4px 0}
#doc blockquote{
  margin:14px 0;padding:10px 16px;background:var(--bg-soft);
  border-left:3px solid var(--line-2);border-radius:0 var(--radius) var(--radius) 0;
  color:var(--fg-2);
}
#doc blockquote p{margin:4px 0}
#doc code{
  font-family:var(--mono);font-size:12.8px;background:var(--bg-code);
  padding:1.5px 5px;border-radius:4px;
}
/* 代码块：配色由 Pygments 主题提供，这里只管边框与留白 */
#doc div.highlight{
  border:1px solid var(--line);border-radius:var(--radius);
  margin:14px 0;overflow:hidden;
}
#doc div.highlight pre{
  margin:0;padding:13px 15px;overflow-x:auto;
  border:none;background:transparent;line-height:1.62;
}
#doc div.highlight pre code{
  background:none;padding:0;font-size:12.6px;white-space:pre;font-family:var(--mono);
}
/* 兜底：未被 .highlight 包裹的 pre */
#doc pre{
  background:var(--bg-code);border:1px solid var(--line);border-radius:var(--radius);
  padding:13px 15px;overflow-x:auto;margin:14px 0;line-height:1.6;
}
#doc pre code{background:none;padding:0;font-size:12.6px;white-space:pre;font-family:var(--mono)}

/* ---------- 代码高亮（Pygments）---------- */
__PYG_LIGHT__
@media (prefers-color-scheme: dark){
__PYG_DARK__
}
#doc hr{border:none;border-top:1px solid var(--line);margin:40px 0}

/* 表格：斑马纹 + 首列加粗 + 横向滚动 */
#doc table{
  width:100%;border-collapse:separate;border-spacing:0;margin:14px 0;
  font-size:13.2px;display:block;overflow-x:auto;
}
#doc thead th{
  background:var(--bg-soft);font-weight:600;text-align:left;
  padding:8px 12px;border-bottom:1px solid var(--line-2);white-space:nowrap;
}
#doc tbody td{padding:7px 12px;border-bottom:1px solid var(--line);vertical-align:top}
#doc tbody tr:nth-child(even){background:var(--bg-soft)}
#doc tbody tr:hover{background:var(--accent-soft)}
#doc tbody td:first-child{font-weight:500}

/* 折叠块 */
#doc details{
  margin:12px 0;border:1px solid var(--line);border-radius:var(--radius);
  background:var(--bg-soft);padding:0 14px;
}
#doc details[open]{padding-bottom:10px}
#doc summary{
  cursor:pointer;padding:10px 0;font-size:13.5px;color:var(--fg-2);
  list-style:none;user-select:none;
}
#doc summary::-webkit-details-marker{display:none}
#doc summary::before{content:"▸ ";color:var(--fg-3)}
#doc details[open] summary::before{content:"▾ "}
#doc summary:hover{color:var(--accent)}
#doc details > p:first-of-type{margin-top:2px}

/* 章节折叠 */
section.foldable.folded > *:not(h2){display:none}
section.foldable.folded > h2{margin-bottom:6px}
h2 .fold-btn{
  float:right;font-size:11px;font-weight:400;color:var(--fg-3);
  border:1px solid var(--line-2);border-radius:999px;padding:1px 9px;
  cursor:pointer;background:var(--bg);margin-top:6px;
}
h2 .fold-btn:hover{color:var(--accent);border-color:var(--accent)}

#totop{
  position:fixed;right:26px;bottom:26px;width:40px;height:40px;border-radius:50%;
  border:1px solid var(--line-2);background:var(--bg);color:var(--fg-2);
  cursor:pointer;display:none;z-index:55;font-size:16px;line-height:1;
}
#totop:hover{color:var(--accent);border-color:var(--accent)}
#totop.show{display:block}

@media (max-width:1000px){
  :root{--side-w:0px}
  #side{transform:translateX(-100%);transition:transform .2s;width:280px}
  #side.open{transform:none;box-shadow:0 0 24px rgba(0,0,0,.12)}
  #main{margin-left:0;padding:0 18px 120px}
  #menu{
    position:fixed;left:14px;top:14px;z-index:58;width:38px;height:38px;border-radius:8px;
    border:1px solid var(--line-2);background:var(--bg);color:var(--fg-2);cursor:pointer;display:none;
  }
  #menu.show{display:block}
}
@media (min-width:1001px){ #menu{display:none} }
</style>
</head>
<body>
<div id="progress"></div>
<button id="menu" aria-label="目录">☰</button>

<aside id="side">
  <div class="hd">
    <div class="t">Deep Explore 项目总览</div>
    <div class="s">__HN__ 个章节 · 点击跳转</div>
    <div class="btns">
      <button data-act="collapse-all">全部折叠</button>
      <button data-act="expand-all">全部展开</button>
      <button data-act="top">回到顶部</button>
    </div>
  </div>
  <nav id="nav">
__NAV__
  </nav>
</aside>

<main id="main">
  <article id="doc">
__BODY__
  </article>
</main>

<button id="totop" aria-label="回到顶部">↑</button>

<script>
(function(){
  var nav=[].slice.call(document.querySelectorAll('.nav-item'));
  var heads=nav.map(function(a){return document.getElementById(a.dataset.id)});
  var navBox=document.getElementById('nav');

  /* --- 把每个 h2 之后的内容包进 section，支持整章折叠 --- */
  var doc=document.getElementById('doc');
  function navFor(id){
    for(var i=0;i<nav.length;i++){ if(nav[i].dataset.id===id) return nav[i]; }
    return null;
  }
  function setChapter(sec,folded){
    sec.classList.toggle('folded',!!folded);
    var fb=sec.querySelector('h2 .fold-btn');
    if(fb) fb.textContent = folded ? '展开' : '折叠';
  }
  function syncChapterNav(sec,folded){
    var h2=sec.querySelector('h2'); if(!h2) return;
    var a=navFor(h2.id); if(!a) return;
    a.classList.toggle('collapsed',!!folded);
    var x=a.nextElementSibling;
    while(x && x.classList.contains('lvl3')){ x.style.display = folded?'none':''; x=x.nextElementSibling; }
  }
  (function wrap(){
    var nodes=[].slice.call(doc.children), cur=null;
    nodes.forEach(function(n){
      if(n.tagName==='H2'){
        cur=document.createElement('section');
        cur.className='foldable';
        doc.insertBefore(cur,n);
        cur.appendChild(n);
        var b=document.createElement('span');
        b.className='fold-btn'; b.textContent='折叠';
        b.onclick=function(){
          var folded=!cur.classList.contains('folded');
          setChapter(cur,folded); syncChapterNav(cur,folded);
        };
        n.appendChild(b);
      } else if(cur){ cur.appendChild(n); }
    });
  })();

  /* --- 侧边栏 H2 手动收起其 H3 --- */
  nav.forEach(function(a){
    if(a.classList.contains('lvl2')){
      a.querySelector('.caret').onclick=function(e){
        e.preventDefault(); e.stopPropagation();
        var collapsed=!a.classList.contains('collapsed');
        a.classList.toggle('collapsed',collapsed);
        var n=a.nextElementSibling;
        while(n && n.classList.contains('lvl3')){
          n.style.display = collapsed ? 'none' : '';
          n=n.nextElementSibling;
        }
      };
    }
  });
  [].slice.call(document.querySelectorAll('.nav-item.lvl3 .caret')).forEach(function(c){
    c.style.visibility='hidden';
  });

  /* --- 滚动高亮 (scrollspy) --- */
  function topOf(el){ return el.getBoundingClientRect().top + window.scrollY; }
  var lastActive=-1;
  function sync(){
    var y=window.scrollY+140, idx=0;
    for(var i=0;i<heads.length;i++){
      if(heads[i] && topOf(heads[i])<=y) idx=i;
    }
    if(idx===lastActive) return;
    nav.forEach(function(a,i){ a.classList.toggle('active', i===idx); });
    lastActive=idx;
    var act=nav[idx];
    if(act){
      var r=act.getBoundingClientRect(), nr=navBox.getBoundingClientRect();
      if(r.top<nr.top+10 || r.bottom>nr.bottom-10){
        navBox.scrollTop += r.top-nr.top-120;
      }
    }
  }

  /* --- 进度条 + 回到顶部 --- */
  var bar=document.getElementById('progress'), top=document.getElementById('totop');
  function onScroll(){
    var h=document.documentElement.scrollHeight-window.innerHeight;
    bar.style.width=(h>0?(window.scrollY/h*100):0)+'%';
    top.classList.toggle('show', window.scrollY>600);
    sync();
  }
  window.addEventListener('scroll',onScroll,{passive:true});
  window.addEventListener('resize',function(){
    lastActive=-1; onScroll();
    document.getElementById('menu').classList.toggle('show', window.innerWidth<=1000);
  });

  top.onclick=function(){window.scrollTo({top:0,behavior:'smooth'})};
  document.getElementById('menu').onclick=function(){
    document.getElementById('side').classList.toggle('open');
  };

  /* --- 全部折叠 / 全部展开 / 回到顶部 --- */
  [].slice.call(document.querySelectorAll('#side .hd button')).forEach(function(b){
    b.onclick=function(){
      var act=b.dataset.act;
      if(act==='top'){ window.scrollTo({top:0,behavior:'smooth'}); return; }
      var fold = (act==='collapse-all');
      [].slice.call(document.querySelectorAll('section.foldable')).forEach(function(s){
        setChapter(s,fold); syncChapterNav(s,fold);
      });
      lastActive=-1; sync();
    };
  });

  /* --- 点击侧栏条目：若目标在折叠章节内则自动展开 --- */
  nav.forEach(function(a){
    a.addEventListener('click',function(){
      var t=document.getElementById(a.dataset.id);
      if(t){
        var sec=t.parentElement;
        while(sec && !(sec.tagName==='SECTION' && sec.classList.contains('foldable'))) sec=sec.parentElement;
        if(sec && sec.classList.contains('folded')){ setChapter(sec,false); syncChapterNav(sec,false); }
      }
      if(window.innerWidth<=1000) document.getElementById('side').classList.remove('open');
    });
  });

  onScroll();
  document.getElementById('menu').classList.toggle('show', window.innerWidth<=1000);

  /* --- 带锚点打开时，包裹章节会改变布局高度，需重新定位一次 --- */
  if(location.hash && location.hash.length>1){
    var target=document.getElementById(decodeURIComponent(location.hash.slice(1)));
    if(target){
      lastActive=-1;
      setTimeout(function(){
        window.scrollTo(0, topOf(target)-8);   /* 用瞬时滚动，避免 smooth 与布局变化打架 */
        onScroll();
      },0);
    }
  }
})();
</script>
</body>
</html>
'''

page = (page
        .replace('__NAV__', nav_html)
        .replace('__BODY__', body)
        .replace('__HN__', str(len(heads)))
        .replace('__PYG_LIGHT__', pyg_light)
        .replace('__PYG_DARK__', pyg_dark))
io.open(OUT, 'w', encoding='utf-8').write(page)
print('HTML 生成完成')
print('侧栏条目:', len(heads), ' H2:', sum(1 for h in heads if h['lvl'] == 2), ' H3:', sum(1 for h in heads if h['lvl'] == 3))
print('高亮代码块:', body.count('class="highlight"'), ' 未高亮 pre:', body.count('<pre>'))
