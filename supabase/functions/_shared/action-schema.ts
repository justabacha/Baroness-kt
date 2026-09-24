import type { ActionDefinition } from './types.ts';

export const ACTION_ALLOWLIST: Record<string, ActionDefinition> = {
  // Media Handlers
  play_music: {
    name: 'play_music',
    parameters: {
      query: { type: 'string', required: false },
      song: { type: 'string', required: false },
      artist: { type: 'string', required: false },
      title: { type: 'string', required: false },
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

  // Playback & Volume Control Handlers
  pause_media: {
    name: 'pause_media',
    parameters: {},
    confirmationRequired: false
  },
  resume_media: {
    name: 'resume_media',
    parameters: {},
    confirmationRequired: false
  },
  stop_media: {
    name: 'stop_media',
    parameters: {},
    confirmationRequired: false
  },
  next_track: {
    name: 'next_track',
    parameters: {},
    confirmationRequired: false
  },
  previous_track: {
    name: 'previous_track',
    parameters: {},
    confirmationRequired: false
  },
  set_volume: {
    name: 'set_volume',
    parameters: {
      level: { type: 'number', required: true, min: 0, max: 100 }
    },
    confirmationRequired: false
  },
  volume_up: {
    name: 'volume_up',
    parameters: {},
    confirmationRequired: false
  },
  volume_down: {
    name: 'volume_down',
    parameters: {},
    confirmationRequired: false
  },
  mute: {
    name: 'mute',
    parameters: {},
    confirmationRequired: false
  },
  unmute: {
    name: 'unmute',
    parameters: {},
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
