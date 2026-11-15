/*
 * Lite mode: make semi-transparent things opaque. Translucent layers are composited
 * on the CPU when WebRender runs in software, which costs time on every frame.
 *
 * - opacity between 0 and 1          -> opacity 1
 * - background rgba(..., 0 < a < 1)  -> same colour, fully opaque
 * Fully transparent elements (opacity 0 / alpha 0) are left alone: pages use them to hide
 * menus, dialogs and tooltips.
 *
 * Work is done in small slices so long pages do not freeze while loading.
 */
(function () {
  'use strict';
  var RGBA = /^rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)$/;
  var queue = [];
  var scheduled = false;

  function fix(el) {
    if (!el || el.nodeType !== 1 || !el.isConnected) {
      return;
    }
    var style = window.getComputedStyle(el);
    if (!style) {
      return;
    }
    var opacity = parseFloat(style.opacity);
    if (opacity > 0 && opacity < 1) {
      el.style.setProperty('opacity', '1', 'important');
    }
    var match = RGBA.exec(style.backgroundColor);
    if (match) {
      var alpha = parseFloat(match[4]);
      if (alpha > 0 && alpha < 1) {
        el.style.setProperty('background-color',
            'rgb(' + match[1] + ', ' + match[2] + ', ' + match[3] + ')', 'important');
      }
    }
  }

  function work() {
    scheduled = false;
    var start = Date.now();
    while (queue.length && Date.now() - start < 12) {
      fix(queue.shift());
    }
    if (queue.length) {
      schedule();
    }
  }

  function schedule() {
    if (!scheduled) {
      scheduled = true;
      setTimeout(work, 16);
    }
  }

  function addTree(root) {
    if (!root || root.nodeType !== 1) {
      return;
    }
    queue.push(root);
    var all = root.getElementsByTagName('*');
    for (var i = 0; i < all.length; i++) {
      queue.push(all[i]);
    }
    schedule();
  }

  addTree(document.documentElement);
  new MutationObserver(function (mutations) {
    for (var i = 0; i < mutations.length; i++) {
      var added = mutations[i].addedNodes;
      for (var j = 0; j < added.length; j++) {
        addTree(added[j]);
      }
    }
  }).observe(document.documentElement, {childList: true, subtree: true});
})();
