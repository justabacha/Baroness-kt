/**
 * Delay Module
 * Governed by Spec 03 §8 and Philosophy §6
 */

const EMOTIONAL_KEYWORDS = [
  'sad', 'angry', 'hurt', 'scared', 'worried', 'stressed', 'depressed',
  'anxious', 'crying', 'upset', 'heartbroken', 'lonely', 'overwhelmed',
  'exhausted', 'burnt out', "can't cope", 'giving up', 'hopeless'
];

export function isEmotionallyWeighted(message: string): boolean {
  const lower = message.toLowerCase();
  return EMOTIONAL_KEYWORDS.some((keyword) => lower.includes(keyword));
}

interface DelayInput {
  replyText: string;
  isCommand: boolean;
  isEmotionallyWeighted: boolean;
}

export function computeDelay(input: DelayInput): number {
  if (input.isCommand) return 300; // near-instant, minimal indicator flash

  const CHARS_PER_MS = 0.06; // ~60 wpm equivalent typing simulation
  const baseTyping = Math.min(input.replyText.length / CHARS_PER_MS, 6000); // cap at 6s
  const emotionalPause = input.isEmotionallyWeighted ? 1500 : 0;

  return Math.round(emotionalPause + baseTyping + 400); // +400ms minimum floor
}
