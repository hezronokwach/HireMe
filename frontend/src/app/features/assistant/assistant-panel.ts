import {
  Component,
  ElementRef,
  afterRenderEffect,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { ListingCard } from '../../api';
import { AuthService } from '../../core/auth/auth.service';
import { formatRange } from '../../core/dates';
import { categoryIcon, formatKes } from '../equipment/equipment-labels';
import { AssistantChat, MAX_MESSAGE_LENGTH } from './assistant-chat.service';

/** Ways to start, shown before the first message. */
export const STARTERS = [
  'I need a tractor in Nakuru next weekend',
  'What can I hire for a wedding of 200 guests in Mombasa?',
  'Something to dig a foundation in Nairobi, under 30,000 a day',
];

/** "Ask HireMe": a conversation with the hire assistant, with listing cards it can hand off to booking. */
@Component({
  selector: 'app-assistant-panel',
  imports: [RouterLink, MatButtonModule, MatIconModule],
  templateUrl: './assistant-panel.html',
  styleUrl: './assistant-panel.scss',
})
export class AssistantPanel {
  protected readonly chat = inject(AssistantChat);
  private readonly auth = inject(AuthService);

  protected readonly starters = STARTERS;
  protected readonly maxLength = MAX_MESSAGE_LENGTH;
  protected readonly formatKes = formatKes;
  protected readonly formatRange = formatRange;
  protected readonly categoryIcon = categoryIcon;
  protected readonly isClient = computed(() => this.auth.hasRole('CLIENT'));
  protected readonly draft = signal('');
  /** Suggestions are offered only under the latest answer. */
  protected readonly lastIndex = computed(() => this.chat.entries().length - 1);

  private readonly log = viewChild<ElementRef<HTMLElement>>('log');
  private readonly input = viewChild<ElementRef<HTMLTextAreaElement>>('input');

  constructor() {
    // Keep the newest message in view.
    afterRenderEffect(() => {
      this.chat.entries();
      this.chat.sending();
      const log = this.log()?.nativeElement;
      if (log) {
        log.scrollTop = log.scrollHeight;
      }
    });
  }

  protected send(text = this.draft()): void {
    if (!text.trim() || this.chat.sending()) {
      return;
    }
    this.chat.send(text);
    this.draft.set('');
    // Also clear the box itself: the binding may not have seen the typed text yet.
    const input = this.input()?.nativeElement;
    if (input) {
      input.value = '';
    }
  }

  protected onKeydown(event: KeyboardEvent): void {
    // Enter sends; Shift+Enter makes a new line.
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  protected onInput(event: Event): void {
    this.draft.set((event.target as HTMLTextAreaElement).value);
  }

  /** Query parameters that open a listing with the days already picked. */
  protected datesFor(card: ListingCard): Record<string, string> {
    return card.quote?.from && card.quote.to ? { from: card.quote.from, to: card.quote.to } : {};
  }
}
