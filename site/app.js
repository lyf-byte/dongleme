(function () {
  'use strict'

  /* 导航滚动态 */
  var nav = document.getElementById('nav')
  function syncNav() {
    if (!nav) return
    nav.classList.toggle('is-scrolled', window.scrollY > 12)
  }
  syncNav()
  window.addEventListener('scroll', syncNav, { passive: true })

  /* 滚动入场 */
  var targets = document.querySelectorAll('.reveal')
  if (!('IntersectionObserver' in window)) {
    targets.forEach(function (el) { el.classList.add('is-in') })
    return
  }

  var observer = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (!entry.isIntersecting) return
      entry.target.classList.add('is-in')
      observer.unobserve(entry.target)
    })
  }, { rootMargin: '0px 0px -10% 0px', threshold: 0.1 })

  targets.forEach(function (el) { observer.observe(el) })

  /* 兜底：个别环境（无头浏览器、部分旧 WebView）不触发观察回调，
     一秒后把已经在视口里的元素直接显示出来，避免整页空白 */
  setTimeout(function () {
    targets.forEach(function (el) {
      if (el.classList.contains('is-in')) return
      var rect = el.getBoundingClientRect()
      if (rect.top < window.innerHeight && rect.bottom > 0) {
        el.classList.add('is-in')
      }
    })
  }, 900)

  /* APK 还没上传时，按钮优雅降级，不做成死链 */
  var apk = document.getElementById('apk')
  var apkLabel = document.getElementById('apk-label')
  if (apk && apkLabel) {
    fetch(apk.getAttribute('href'), { method: 'HEAD' })
      .then(function (res) {
        if (res.ok) return
        apkLabel.textContent = 'Android 版准备中'
        apk.classList.add('is-pending')
        apk.removeAttribute('download')
        apk.setAttribute('href', 'https://dongleme.xyz')
      })
      .catch(function () {
        /* 本地 file:// 打开时请求必然失败，保持默认即可 */
      })
  }
})()
