<script setup>
import { uiState, closeDialog } from '../utils/ui'
</script>

<template>
  <transition name="toast">
    <div v-if="uiState.toast.visible" class="ui-toast">{{ uiState.toast.message }}</div>
  </transition>

  <transition name="ui">
    <div v-if="uiState.dialog.visible" class="ui-overlay" @click.self="closeDialog(false)">
      <div class="ui-dialog" role="dialog" aria-modal="true">
        <div class="ui-dialog__body">
          <h3>{{ uiState.dialog.title }}</h3>
          <p>{{ uiState.dialog.message }}</p>
        </div>
        <div class="ui-dialog__actions">
          <button type="button" class="ui-dialog__cancel" @click="closeDialog(false)">
            {{ uiState.dialog.cancelText }}
          </button>
          <button
            type="button"
            class="ui-dialog__confirm"
            :class="{ danger: uiState.dialog.danger }"
            @click="closeDialog(true)"
          >
            {{ uiState.dialog.confirmText }}
          </button>
        </div>
      </div>
    </div>
  </transition>
</template>
