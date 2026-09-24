export type ActionParamType = 'string' | 'number' | 'boolean';

export interface ActionParameterSchema {
  type: ActionParamType;
  required: boolean;
  min?: number;
  max?: number;
}

export interface ActionDefinition {
  name: string;
  parameters: Record<string, ActionParameterSchema>;
  confirmationRequired: boolean;
}

export interface ClassifierResult {
  type: 'COMMAND' | 'CONVERSATION';
  intent?: string;
  parameters?: Record<string, unknown>;
}

export interface ValidationResult {
  valid: boolean;
  action?: { intent: string; parameters: Record<string, unknown> };
  reason?: string;
}
