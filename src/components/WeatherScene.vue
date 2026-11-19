<script setup>
import { computed, useId } from 'vue'

const props = defineProps({
  type: { type: String, default: 'cloudy' },
  isDay: { type: Boolean, default: true }
})

const uid = useId()
const url = (name) => `url(#${uid}-${name})`
const href = (name) => `#${uid}-${name}`

const showSun = computed(() => props.type === 'sunny' && props.isDay)
const showDimSun = computed(() => props.isDay && ['fog', 'haze'].includes(props.type))
const showClouds = computed(() =>
  ['cloudy', 'overcast', 'rain', 'snow', 'thunder', 'fog', 'haze'].includes(props.type)
)
const darkClouds = computed(() => ['rain', 'thunder'].includes(props.type))
const showRain = computed(() => ['rain', 'thunder'].includes(props.type))
const showSnow = computed(() => props.type === 'snow')
const showThunder = computed(() => props.type === 'thunder')
const showFog = computed(() => props.type === 'fog')
const showHaze = computed(() => props.type === 'haze')
const showStars = computed(
  () => !props.isDay && !['rain', 'thunder', 'snow'].includes(props.type)
)

const frontFill = computed(() =>
  url(darkClouds.value ? 'cloudDark' : props.type === 'overcast' ? 'cloudGray' : 'cloudLight')
)
const backFill = computed(() => url(darkClouds.value ? 'cloudBackDark' : 'cloudBackLight'))

const rays = Array.from({ length: 12 }, (_, index) => ({
  angle: index * 30,
  length: index % 2 === 0 ? 15 : 10,
  width: index % 2 === 0 ? 3 : 2.2
}))

const sparkles = [
  { x: 66, y: 36, scale: 1, delay: 0 },
  { x: 178, y: 100, scale: 0.7, delay: 1.4 },
  { x: 92, y: 16, scale: 0.55, delay: 2.6 }
]

const dropsNear = Array.from({ length: 8 }, (_, index) => ({
  key: index,
  x: 52 + index * 13,
  length: index % 3 === 0 ? 18 : 13,
  width: index % 3 === 0 ? 3 : 2.2,
  delay: index * 0.17,
  duration: 0.8 + (index % 3) * 0.15
}))
const dropsFar = Array.from({ length: 5 }, (_, index) => ({
  key: index,
  x: 60 + index * 22,
  delay: index * 0.31,
  duration: 1.25 + (index % 2) * 0.2
}))
const splashes = [
  { x: 66, delay: 0.2 },
  { x: 104, delay: 0.55 },
  { x: 142, delay: 0.9 }
]

const flakes = Array.from({ length: 14 }, (_, index) => ({
  key: index,
  x: 42 + (index % 7) * 18,
  y: 128 + Math.floor(index / 7) * 14,
  r: index % 3 === 0 ? 3.4 : 2.2,
  crystal: index % 5 === 0,
  far: index % 4 === 3,
  opacity: 0.65 + (index % 3) * 0.15,
  delay: index * 0.4,
  duration: 4.5 + (index % 4) * 0.8,
  swayDuration: 2 + (index % 3) * 0.6
}))

const fogBands = [
  { y: 116, x: 34, width: 128, height: 9, opacity: 0.6, duration: 7, delay: 0 },
  { y: 132, x: 22, width: 150, height: 8, opacity: 0.45, duration: 9, delay: 0.8 },
  { y: 148, x: 44, width: 118, height: 7, opacity: 0.34, duration: 6.5, delay: 1.5 },
  { y: 162, x: 30, width: 138, height: 6, opacity: 0.22, duration: 10, delay: 2.2 }
]

const hazeDots = Array.from({ length: 14 }, (_, index) => ({
  key: index,
  x: 30 + (index % 5) * 30,
  y: 96 + Math.floor(index / 5) * 26,
  r: index % 4 === 0 ? 3.6 : 2.2,
  delay: index * 0.4,
  duration: 5 + (index % 3) * 1.5
}))

const stars = [
  { x: 30, y: 30, r: 1.9 },
  { x: 58, y: 52, r: 1.2 },
  { x: 84, y: 24, r: 1.6 },
  { x: 46, y: 88, r: 1.1 },
  { x: 96, y: 64, r: 1.3 },
  { x: 20, y: 64, r: 1.4 },
  { x: 172, y: 28, r: 1.7 },
  { x: 182, y: 108, r: 1.2 },
  { x: 62, y: 118, r: 1.1 },
  { x: 108, y: 40, r: 1 }
]
</script>

<template>
  <svg
    class="weather-scene"
    :class="[`weather-scene--${type}`, isDay ? 'is-day' : 'is-night']"
    viewBox="0 0 200 200"
    preserveAspectRatio="xMidYMid slice"
    aria-hidden="true"
  >
    <defs>
      <radialGradient :id="`${uid}-sunCore`" cx="42%" cy="38%" r="72%">
        <stop offset="0%" stop-color="#fffef2" />
        <stop offset="42%" stop-color="#ffe9a0" />
        <stop offset="78%" stop-color="#ffc84a" />
        <stop offset="100%" stop-color="#ff9d2e" />
      </radialGradient>
      <radialGradient :id="`${uid}-sunHalo`" cx="50%" cy="50%" r="50%">
        <stop offset="0%" stop-color="rgba(255,220,120,.6)" />
        <stop offset="55%" stop-color="rgba(255,190,80,.22)" />
        <stop offset="100%" stop-color="rgba(255,180,60,0)" />
      </radialGradient>
      <radialGradient :id="`${uid}-moonGlow`" cx="50%" cy="50%" r="50%">
        <stop offset="0%" stop-color="rgba(255,250,214,.5)" />
        <stop offset="60%" stop-color="rgba(255,250,214,.14)" />
        <stop offset="100%" stop-color="rgba(255,250,214,0)" />
      </radialGradient>
      <linearGradient :id="`${uid}-cloudLight`" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#ffffff" />
        <stop offset="72%" stop-color="#eaf3fd" />
        <stop offset="100%" stop-color="#cfe0f4" />
      </linearGradient>
      <linearGradient :id="`${uid}-cloudGray`" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#eef2f7" />
        <stop offset="100%" stop-color="#aebfd2" />
      </linearGradient>
      <linearGradient :id="`${uid}-cloudDark`" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#a9b8cb" />
        <stop offset="100%" stop-color="#5d6f85" />
      </linearGradient>
      <linearGradient :id="`${uid}-cloudBackLight`" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="rgba(255,255,255,.6)" />
        <stop offset="100%" stop-color="rgba(203,213,225,.16)" />
      </linearGradient>
      <linearGradient :id="`${uid}-cloudBackDark`" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="rgba(148,163,184,.5)" />
        <stop offset="100%" stop-color="rgba(71,85,105,.18)" />
      </linearGradient>
      <linearGradient :id="`${uid}-rainGrad`" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="rgba(207,240,253,0)" />
        <stop offset="35%" stop-color="rgba(207,240,253,.95)" />
        <stop offset="100%" stop-color="rgba(124,196,250,.25)" />
      </linearGradient>
      <linearGradient :id="`${uid}-boltGrad`" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#fff8d6" />
        <stop offset="100%" stop-color="#ffc93b" />
      </linearGradient>
      <linearGradient :id="`${uid}-fogBand`" x1="0" y1="0" x2="1" y2="0">
        <stop offset="0%" stop-color="rgba(255,255,255,0)" />
        <stop offset="18%" stop-color="rgba(255,255,255,.9)" />
        <stop offset="82%" stop-color="rgba(255,255,255,.9)" />
        <stop offset="100%" stop-color="rgba(255,255,255,0)" />
      </linearGradient>
      <linearGradient :id="`${uid}-shootTail`" x1="0" y1="0" x2="1" y2="0">
        <stop offset="0%" stop-color="rgba(255,255,255,0)" />
        <stop offset="100%" stop-color="rgba(255,255,255,.95)" />
      </linearGradient>
      <filter :id="`${uid}-softGlow`" x="-60%" y="-60%" width="220%" height="220%">
        <feGaussianBlur stdDeviation="3" result="blur" />
        <feMerge>
          <feMergeNode in="blur" />
          <feMergeNode in="SourceGraphic" />
        </feMerge>
      </filter>
      <filter :id="`${uid}-blurFar`" x="-40%" y="-40%" width="180%" height="180%">
        <feGaussianBlur stdDeviation="1.8" />
      </filter>
      <filter :id="`${uid}-blurSoft`" x="-30%" y="-30%" width="160%" height="160%">
        <feGaussianBlur stdDeviation="1" />
      </filter>
      <path
        :id="`${uid}-cloudShape`"
        d="M28 0C12 0 0 -10 0 -22C0 -34 10 -43 22 -43C26 -58 40 -68 56 -68C70 -68 82 -60 87 -48C90 -49 94 -50 98 -50C112 -50 122 -40 122 -27C122 -12 110 0 96 0Z"
      />
      <path
        :id="`${uid}-sparkle`"
        d="M0 -7C0.9 -2.4 2.4 -0.9 7 0C2.4 0.9 0.9 2.4 0 7C-0.9 2.4 -2.4 0.9 -7 0C-2.4 -0.9 -0.9 -2.4 0 -7Z"
      />
      <mask :id="`${uid}-moonMask`">
        <circle cx="140" cy="60" r="23" fill="#fff" />
        <circle cx="151" cy="52" r="20" fill="#000" />
      </mask>
    </defs>

    <!-- 晴天太阳 -->
    <g v-if="showSun" class="sun">
      <circle cx="138" cy="62" r="54" :fill="url('sunHalo')" class="sun-halo" />
      <circle
        cx="138"
        cy="62"
        r="31"
        fill="none"
        stroke="rgba(255,244,214,.3)"
        stroke-width="1.4"
        stroke-dasharray="3 9"
        stroke-linecap="round"
        class="sun-ring"
      />
      <g class="sun-rays" stroke="rgba(255,244,214,.85)" stroke-linecap="round">
        <line
          v-for="ray in rays"
          :key="ray.angle"
          x1="138"
          y1="27"
          x2="138"
          :y2="27 - ray.length"
          :stroke-width="ray.width"
          :transform="`rotate(${ray.angle} 138 62)`"
        />
      </g>
      <circle cx="138" cy="62" r="24" :fill="url('sunCore')" :filter="url('softGlow')" class="sun-core" />
      <circle cx="130" cy="54" r="7" fill="rgba(255,255,255,.5)" :filter="url('blurSoft')" />
      <g class="sparkles" fill="#fff8dc">
        <g
          v-for="(spark, index) in sparkles"
          :key="index"
          :transform="`translate(${spark.x} ${spark.y}) scale(${spark.scale})`"
        >
          <use :href="href('sparkle')" :class="`sparkle sparkle--${index}`" />
        </g>
      </g>
    </g>

    <!-- 雾 / 霾后的暗淡太阳 -->
    <g v-if="showDimSun" class="dim-sun">
      <circle cx="138" cy="62" r="40" :fill="url('sunHalo')" opacity=".5" />
      <circle
        cx="138"
        cy="62"
        r="22"
        :fill="url('sunCore')"
        :opacity="type === 'haze' ? 0.55 : 0.4"
        :filter="url('blurSoft')"
        class="dim-sun-core"
      />
    </g>

    <!-- 夜晚月亮与星空 -->
    <g v-if="!isDay" class="moon">
      <circle cx="140" cy="60" r="48" :fill="url('moonGlow')" class="moon-halo" />
      <g class="moon-body" :filter="url('softGlow')">
        <circle cx="140" cy="60" r="23" fill="#fdf6c9" :mask="url('moonMask')" />
        <g :mask="url('moonMask')" fill="rgba(190,170,90,.28)">
          <circle cx="130" cy="66" r="3.4" />
          <circle cx="137" cy="76" r="2.2" />
          <circle cx="126" cy="52" r="1.8" />
        </g>
      </g>
      <g v-if="showStars" class="stars" fill="#fff">
        <circle
          v-for="(star, index) in stars"
          :key="index"
          :cx="star.x"
          :cy="star.y"
          :r="star.r"
          :class="`star star--${index % 4}`"
        />
        <g class="shooting-star">
          <line x1="0" y1="0" x2="34" y2="0" :stroke="url('shootTail')" stroke-width="2" stroke-linecap="round" />
          <circle cx="34" cy="0" r="2" fill="#fff" />
        </g>
      </g>
    </g>

    <!-- 云层 -->
    <g v-if="showClouds" class="cloud cloud--back" :filter="url('blurFar')">
      <use :href="href('cloudShape')" :fill="backFill" transform="translate(96 108) scale(0.62)" />
    </g>
    <g v-if="type === 'overcast'" class="cloud cloud--high" :filter="url('blurFar')">
      <use :href="href('cloudShape')" :fill="backFill" transform="translate(6 96) scale(0.5)" />
    </g>
    <g v-if="showClouds" class="cloud cloud--front">
      <use :href="href('cloudShape')" :fill="frontFill" transform="translate(36 142)" />
      <ellipse
        cx="92"
        cy="86"
        rx="22"
        ry="9"
        fill="rgba(255,255,255,.4)"
        :filter="url('blurSoft')"
        class="cloud-rim"
      />
      <ellipse
        v-if="showThunder"
        cx="96"
        cy="112"
        rx="52"
        ry="26"
        fill="rgba(255,244,200,.9)"
        :filter="url('blurFar')"
        class="cloud-flash"
      />
    </g>

    <!-- 雨：远景 + 近景 + 溅落 -->
    <g v-if="showRain" class="rain">
      <g class="rain-far" :filter="url('blurFar')" stroke="rgba(190,225,250,.4)" stroke-width="1.6" stroke-linecap="round">
        <line
          v-for="drop in dropsFar"
          :key="drop.key"
          :x1="drop.x"
          y1="142"
          :x2="drop.x - 3"
          y2="154"
          :style="{ animationDelay: `${drop.delay}s`, animationDuration: `${drop.duration}s` }"
        />
      </g>
      <g class="rain-near" :stroke="url('rainGrad')" stroke-linecap="round">
        <line
          v-for="drop in dropsNear"
          :key="drop.key"
          :x1="drop.x"
          y1="144"
          :x2="drop.x - 4"
          :y2="144 + drop.length"
          :stroke-width="drop.width"
          :style="{ animationDelay: `${drop.delay}s`, animationDuration: `${drop.duration}s` }"
        />
      </g>
      <g class="splashes" fill="none" stroke="rgba(207,240,253,.75)" stroke-width="1.6" stroke-linecap="round">
        <path
          v-for="splash in splashes"
          :key="splash.x"
          :d="`M${splash.x - 6} 178 q6 -7 12 0`"
          :style="{ animationDelay: `${splash.delay}s` }"
        />
      </g>
    </g>

    <!-- 雪：飘落 + 摇摆 + 冰晶 -->
    <g v-if="showSnow" class="snow">
      <g
        v-for="flake in flakes"
        :key="flake.key"
        class="flake-fall"
        :style="{ animationDelay: `${flake.delay}s`, animationDuration: `${flake.duration}s` }"
      >
        <g
          class="flake-sway"
          :style="{ animationDuration: `${flake.swayDuration}s`, animationDelay: `${flake.delay}s` }"
        >
          <g v-if="flake.crystal" class="flake-spin" stroke="#fff" stroke-width="1.5" stroke-linecap="round" :opacity="flake.opacity">
            <path :d="`M${flake.x} ${flake.y - 7} V${flake.y + 7} M${flake.x - 6} ${flake.y - 3.5} L${flake.x + 6} ${flake.y + 3.5} M${flake.x - 6} ${flake.y + 3.5} L${flake.x + 6} ${flake.y - 3.5}`" />
          </g>
          <circle
            v-else
            :cx="flake.x"
            :cy="flake.y"
            :r="flake.r"
            fill="#fff"
            :opacity="flake.opacity"
            :filter="flake.far ? url('blurSoft') : undefined"
          />
        </g>
      </g>
    </g>

    <!-- 闪电 -->
    <g v-if="showThunder" class="thunder">
      <circle cx="102" cy="150" r="30" fill="rgba(255,224,102,.2)" :filter="url('blurFar')" class="thunder-glow" />
      <path
        class="lightning"
        d="M106 128L88 158L100 158L92 186L122 150L108 150L118 128Z"
        :fill="url('boltGrad')"
        :filter="url('softGlow')"
      />
    </g>

    <!-- 雾带 -->
    <g v-if="showFog" class="fog">
      <rect
        v-for="(band, index) in fogBands"
        :key="index"
        :x="band.x"
        :y="band.y"
        :width="band.width"
        :height="band.height"
        :rx="band.height / 2"
        :fill="url('fogBand')"
        :opacity="band.opacity"
        class="fog-band"
        :style="{ animationDuration: `${band.duration}s`, animationDelay: `${band.delay}s` }"
      />
    </g>

    <!-- 霾：漂浮颗粒 -->
    <g v-if="showHaze" class="haze" :filter="url('blurSoft')">
      <circle
        v-for="dot in hazeDots"
        :key="dot.key"
        :cx="dot.x"
        :cy="dot.y"
        :r="dot.r"
        fill="rgba(255,232,180,.65)"
        :style="{ animationDelay: `${dot.delay}s`, animationDuration: `${dot.duration}s` }"
      />
    </g>
  </svg>
</template>

<style scoped>
.weather-scene {
  display: block;
  width: 100%;
  height: 100%;
  overflow: visible;
}

/* 太阳 */
.sun-core {
  animation: sunPulse 4.5s ease-in-out infinite;
  transform-origin: 138px 62px;
}

.sun-halo {
  animation: haloPulse 5.5s ease-in-out infinite;
  transform-origin: 138px 62px;
}

.sun-rays {
  animation: spin 40s linear infinite;
  transform-origin: 138px 62px;
}

.sun-ring {
  animation: spinReverse 60s linear infinite;
  transform-origin: 138px 62px;
}

.sparkle {
  animation: sparkleTwinkle 3.6s ease-in-out infinite;
  transform-box: fill-box;
  transform-origin: center;
}

.sparkle--1 {
  animation-delay: 1.4s;
}

.sparkle--2 {
  animation-delay: 2.6s;
}

.dim-sun-core {
  animation: dimSunBreath 6s ease-in-out infinite;
}

/* 月亮与星空 */
.moon-halo {
  animation: haloPulse 6.5s ease-in-out infinite;
  transform-origin: 140px 60px;
}

.moon-body {
  animation: moonFloat 8s ease-in-out infinite alternate;
}

.star {
  animation: twinkle 3.4s ease-in-out infinite;
  transform-box: fill-box;
  transform-origin: center;
}

.star--1 {
  animation-duration: 4.2s;
  animation-delay: 0.7s;
}

.star--2 {
  animation-duration: 4.9s;
  animation-delay: 1.3s;
}

.star--3 {
  animation-duration: 5.6s;
  animation-delay: 2s;
}

.shooting-star {
  animation: shoot 11s linear infinite;
  opacity: 0;
}

/* 云 */
.cloud--back {
  animation: driftBack 12s ease-in-out infinite alternate;
}

.cloud--high {
  animation: driftBack 16s ease-in-out infinite alternate-reverse;
}

.cloud--front {
  animation: driftFront 9s ease-in-out infinite alternate;
}

.cloud-rim {
  animation: rimShimmer 7s ease-in-out infinite;
}

.cloud-flash {
  animation: cloudFlash 4.2s infinite;
  opacity: 0;
}

/* 雨 */
.rain-near line {
  animation: rainFall 1s linear infinite;
}

.rain-far line {
  animation: rainFall 1.3s linear infinite;
}

.splashes path {
  animation: splash 1.1s ease-out infinite;
  transform-box: fill-box;
  transform-origin: center bottom;
  opacity: 0;
}

/* 雪 */
.flake-fall {
  animation: snowFall 5s linear infinite;
}

.flake-sway {
  animation: snowSway 2.4s ease-in-out infinite alternate;
}

.flake-spin {
  animation: spin 9s linear infinite;
  transform-box: fill-box;
  transform-origin: center;
}

/* 雷电 */
.lightning {
  animation: flash 4.2s infinite;
}

.thunder-glow {
  animation: thunderGlow 4.2s infinite;
}

/* 雾 / 霾 */
.fog-band {
  animation: fogDrift 7s ease-in-out infinite alternate;
}

.haze circle {
  animation: hazeFloat 5.5s ease-in-out infinite;
}

@keyframes sunPulse {
  0%,
  100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.045);
  }
}

@keyframes haloPulse {
  0%,
  100% {
    opacity: 0.6;
    transform: scale(0.95);
  }
  50% {
    opacity: 1;
    transform: scale(1.06);
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@keyframes spinReverse {
  from {
    transform: rotate(360deg);
  }
  to {
    transform: rotate(0deg);
  }
}

@keyframes sparkleTwinkle {
  0%,
  100% {
    opacity: 0.15;
    transform: scale(0.5) rotate(0deg);
  }
  50% {
    opacity: 0.95;
    transform: scale(1) rotate(90deg);
  }
}

@keyframes dimSunBreath {
  0%,
  100% {
    opacity: 0.75;
  }
  50% {
    opacity: 1;
  }
}

@keyframes moonFloat {
  from {
    transform: translateY(-2px);
  }
  to {
    transform: translateY(2px);
  }
}

@keyframes twinkle {
  0%,
  100% {
    opacity: 0.25;
    transform: scale(0.75);
  }
  50% {
    opacity: 1;
    transform: scale(1.2);
  }
}

@keyframes shoot {
  0%,
  74% {
    opacity: 0;
    transform: translate(168px, 18px) rotate(150deg);
  }
  76% {
    opacity: 1;
  }
  82% {
    opacity: 0;
    transform: translate(96px, 60px) rotate(150deg);
  }
  100% {
    opacity: 0;
    transform: translate(96px, 60px) rotate(150deg);
  }
}

@keyframes driftBack {
  from {
    transform: translateX(-6px);
  }
  to {
    transform: translateX(7px);
  }
}

@keyframes driftFront {
  from {
    transform: translateX(5px);
  }
  to {
    transform: translateX(-7px);
  }
}

@keyframes rimShimmer {
  0%,
  100% {
    opacity: 0.55;
  }
  50% {
    opacity: 0.95;
  }
}

@keyframes cloudFlash {
  0%,
  82%,
  100% {
    opacity: 0;
  }
  84%,
  88% {
    opacity: 0.55;
  }
  86% {
    opacity: 0.15;
  }
}

@keyframes rainFall {
  0% {
    opacity: 0;
    transform: translateY(-10px);
  }
  20% {
    opacity: 0.95;
  }
  85% {
    opacity: 0.8;
  }
  100% {
    opacity: 0;
    transform: translateY(30px);
  }
}

@keyframes splash {
  0%,
  62% {
    opacity: 0;
    transform: scale(0.3);
  }
  70% {
    opacity: 0.85;
    transform: scale(0.7);
  }
  100% {
    opacity: 0;
    transform: scale(1.25);
  }
}

@keyframes snowFall {
  0% {
    opacity: 0;
    transform: translateY(-14px);
  }
  12% {
    opacity: 1;
  }
  88% {
    opacity: 0.9;
  }
  100% {
    opacity: 0;
    transform: translateY(46px);
  }
}

@keyframes snowSway {
  from {
    transform: translateX(-4px);
  }
  to {
    transform: translateX(4px);
  }
}

@keyframes flash {
  0%,
  82%,
  100% {
    opacity: 0.42;
  }
  84%,
  88% {
    opacity: 1;
  }
  86% {
    opacity: 0.55;
  }
  90% {
    opacity: 0.85;
  }
  92% {
    opacity: 0.42;
  }
}

@keyframes thunderGlow {
  0%,
  82%,
  100% {
    opacity: 0.4;
  }
  84%,
  88% {
    opacity: 1;
  }
  86% {
    opacity: 0.55;
  }
}

@keyframes fogDrift {
  from {
    transform: translateX(-9px);
  }
  to {
    transform: translateX(10px);
  }
}

@keyframes hazeFloat {
  0%,
  100% {
    opacity: 0.25;
    transform: translate(0, 0);
  }
  50% {
    opacity: 0.85;
    transform: translate(5px, -6px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .weather-scene * {
    animation: none !important;
  }
}
</style>