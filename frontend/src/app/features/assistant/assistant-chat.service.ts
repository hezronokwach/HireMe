import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import {
  AIService,
  AssistantChatRequest,
  AssistantChatResponse,
  ListingCard,
  Message,
} from '../../api';
import { errorMessage } from '../../core/api-errors';

/** The API takes at most 20 messages; older ones are left out (the model only sees the latest anyway). */
export const MAX_MESSAGES = 20;
export const MAX_MESSAGE_LENGTH = 1000;
const STORAGE_KEY = 'hireme.assistant';

export interface ChatEntry {
  role: 'USER' | 'ASSISTANT';
  text: string;
  /** Assistant only: listing cards built by the server. */
  listings?: ListingCard[];
  from?: string;
  to?: string;
  suggestions?: string[];
}

/**
 * The conversation with the hire assistant. The server keeps nothing, so the whole conversation is
 * sent with every message. It is kept for the browser tab (sessionStorage), so going to a listing and
 * back doesn't lose it.
 */
@Injectable({ providedIn: 'root' })
export class AssistantChat {
  private readonly api = inject(AIService);
  private readonly storage = safeSessionStorage();

  readonly entries = signal<ChatEntry[]>(this.restore());
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  /** The last message that failed, so it can be sent again. */
  private failed: string | null = null;

  readonly canRetry = computed(() => this.error() !== null && !this.sending());

  send(text: string): void {
    const message = text.trim().slice(0, MAX_MESSAGE_LENGTH);
    if (!message || this.sending()) {
      return;
    }
    const conversation = [...this.entries(), { role: 'USER' as const, text: message }];
    this.entries.set(conversation);
    this.save();
    this.ask(conversation, message);
  }

  /** Sends the last message again after an error. */
  retry(): void {
    const conversation = this.entries();
    if (!this.failed || this.sending() || conversation.at(-1)?.role !== 'USER') {
      return;
    }
    this.ask(conversation, this.failed);
  }

  reset(): void {
    this.entries.set([]);
    this.error.set(null);
    this.failed = null;
    this.save();
  }

  private ask(conversation: ChatEntry[], message: string): void {
    this.sending.set(true);
    this.error.set(null);
    this.api.chatWithAssistant(toRequest(conversation)).subscribe({
      next: (response) => {
        this.sending.set(false);
        this.failed = null;
        this.entries.update((entries) => [...entries, fromResponse(response)]);
        this.save();
      },
      error: (err: unknown) => {
        this.sending.set(false);
        this.failed = message;
        this.error.set(
          err instanceof HttpErrorResponse && err.status === 503
            ? 'The assistant is unavailable right now. You can still use the filters below.'
            : errorMessage(err, 'The assistant could not answer. Try again.'),
        );
      },
    });
  }

  private save(): void {
    try {
      this.storage?.setItem(STORAGE_KEY, JSON.stringify(this.entries()));
    } catch {
      // Storage full or blocked: the conversation just won't survive a reload.
    }
  }

  private restore(): ChatEntry[] {
    try {
      const raw = this.storage?.getItem(STORAGE_KEY);
      const parsed: unknown = raw ? JSON.parse(raw) : [];
      return Array.isArray(parsed)
        ? (parsed as ChatEntry[]).filter((e) => e && typeof e.text === 'string')
        : [];
    } catch {
      return [];
    }
  }
}

export function toRequest(conversation: ChatEntry[]): AssistantChatRequest {
  return {
    messages: conversation.slice(-MAX_MESSAGES).map((entry): Message => ({
      role: entry.role as Message['role'],
      text: entry.text,
      listingIds:
        entry.role === 'ASSISTANT' && entry.listings?.length
          ? entry.listings.map((l) => l.id!)
          : undefined,
      // The days an answer was about, so a follow-up ("does it come with a driver?") keeps them.
      from: entry.role === 'ASSISTANT' ? (entry.from ?? undefined) : undefined,
      to: entry.role === 'ASSISTANT' ? (entry.to ?? undefined) : undefined,
    })),
  };
}

function fromResponse(response: AssistantChatResponse): ChatEntry {
  return {
    role: 'ASSISTANT',
    text: response.reply ?? '',
    listings: response.listings ?? [],
    from: response.from,
    to: response.to,
    suggestions: response.suggestions ?? [],
  };
}

function safeSessionStorage(): Storage | null {
  try {
    return globalThis.sessionStorage ?? null;
  } catch {
    return null;
  }
}
