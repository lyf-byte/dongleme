(function () {
  'use strict'

  document.documentElement.classList.add('js')

  var nav = document.getElementById('nav')
  function syncNav() {
    if (!nav) return
    nav.classList.toggle('is-scrolled', window.scrollY > 12)
  }
  syncNav()
  window.addEventListener('scroll', syncNav, { passive: true })

  function initReveal() {
    var targets = document.querySelectorAll('.reveal')
    var reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (!('IntersectionObserver' in window) || reducedMotion) {
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

    setTimeout(function () {
      targets.forEach(function (el) {
        if (el.classList.contains('is-in')) return
        var rect = el.getBoundingClientRect()
        if (rect.top < window.innerHeight && rect.bottom > 0) {
          el.classList.add('is-in')
        }
      })
    }, 900)
  }

  function initMobileNav() {
    var links = document.querySelectorAll('.nav__mobile a')
    if (!('IntersectionObserver' in window)) return

    var sections = Array.from(links).map(function (link) {
      return document.querySelector(link.getAttribute('href'))
    }).filter(Boolean)

    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return
        links.forEach(function (link) {
          if (link.getAttribute('href') === `#${entry.target.id}`) {
            link.setAttribute('aria-current', 'location')
          } else {
            link.removeAttribute('aria-current')
          }
        })
      })
    }, { rootMargin: '-18% 0px -68% 0px', threshold: 0 })

    sections.forEach(function (section) { observer.observe(section) })
  }

  function initApk() {
    var apk = document.getElementById('apk')
    var apkLabel = document.getElementById('apk-label')
    if (!apk || !apkLabel) return

    function showPending() {
      apkLabel.textContent = 'Android 版准备中'
      apk.classList.add('is-pending')
      apk.removeAttribute('download')
      apk.setAttribute('href', 'https://dongleme.xyz')
      apk.setAttribute('aria-label', 'Android 版准备中，前往网页版体验')
    }

    var href = apk.getAttribute('href') || ''

    // 外链下载源（如 Gitee Release）不做 HEAD 探测：跨域请求会被 CORS 拦截，误判成不可用
    if (/^https?:/i.test(href)) return

    fetch(href, { method: 'HEAD', cache: 'no-store' })
      .then(function (res) {
        if (!res.ok) showPending()
      })
      .catch(function () {
        if (window.location.protocol !== 'file:') showPending()
      })
  }

  initReveal()
  initMobileNav()
  initApk()
})()
