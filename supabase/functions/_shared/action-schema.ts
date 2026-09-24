import type { ActionDefinition } from './types.ts';

export const ACTION_ALLOWLIST: Record<string, ActionDefinition> = {
  // Media Handlers
  play_music: {
    name: 'play_music',
    parameters: {
      genre: { type: 'string', required: false },
      app: { type: 'string', required: false }
    },
    confirmationRequired: false
  },
  play_video: {
    name: 'play_video',
    parameters: {
      query: { type: 'string', required: true }
    },
    confirmationRequired: false
  },
  play_voice_note: {
    name: 'play_voice_note',
    parameters: {
      note_id: { type: 'string', required: true }
    },
    confirmationRequired: false
  },

  // Navigation & App Control Handlers
  navigate: {
    name: 'navigate',
    parameters: {
      destination: { type: 'string', required: true }
    },
    confirmationRequired: false
  },
  open_app: {
    name: 'open_app',
    parameters: {
      app_name: { type: 'string', required: true }
    },
    confirmationRequired: false
  },
  open_screen: {
    name: 'open_screen',
    parameters: {
      screen_id: { type: 'string', required: true }
    },
    confirmationRequired: false
  },

  // Clock Handlers
  set_timer: {
    name: 'set_timer',
    parameters: {
      minutes: { type: 'number', required: true, min: 1, max: 1440 }
    },
    confirmationRequired: false
  },
  set_alarm: {
    name: 'set_alarm',
    parameters: {
      hour: { type: 'number', required: true, min: 0, max: 23 },
      minute: { type: 'number', required: true, min: 0, max: 59 }
    },
    confirmationRequired: false
  }
};
