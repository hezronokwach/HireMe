import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

export interface GuideStep {
  title: string;
  text: string;
  link: string;
  query?: Record<string, string>;
}

/** What to try on the owner side, in the order it makes sense. */
export const GUIDE_STEPS: GuideStep[] = [
  {
    title: 'Answer a request',
    text: 'Wanjiku asked for the tractor, the tent and the PA system. Confirm or decline; her phone number is on each request.',
    link: '/owner-bookings',
    query: { status: 'PENDING' },
  },
  {
    title: 'Finish a hire',
    text: 'When equipment comes back, mark the confirmed booking as completed.',
    link: '/owner-bookings',
    query: { status: 'CONFIRMED' },
  },
  {
    title: 'Edit a listing',
    text: 'Change a rate, or put something under maintenance to stop new bookings.',
    link: '/my-listings',
  },
  {
    title: 'Draft one with AI',
    text: 'Give a name and category, and "Write it for me" drafts the description and suggests a rate.',
    link: '/my-listings/new',
  },
];

const COLLAPSED_KEY = 'hireme.demoGuide.collapsed';

/**
 * Shows a visitor around the owner side while they're signed in as the demo owner. It can be folded
 * away (remembered for this browser), and "Leave the demo" signs out.
 */
@Component({
  selector: 'app-demo-guide',
  imports: [RouterLink, RouterLinkActive, MatButtonModule, MatIconModule],
  templateUrl: './demo-guide.html',
  styleUrl: './demo-guide.scss',
})
export class DemoGuide {
  protected readonly auth = inject(AuthService);
  protected readonly steps = GUIDE_STEPS;
  protected readonly collapsed = signal(readCollapsed());

  protected toggle(): void {
    this.collapsed.update((value) => !value);
    try {
      globalThis.localStorage?.setItem(COLLAPSED_KEY, String(this.collapsed()));
    } catch {
      // Storage blocked: the choice just isn't remembered.
    }
  }
}

function readCollapsed(): boolean {
  try {
    return globalThis.localStorage?.getItem(COLLAPSED_KEY) === 'true';
  } catch {
    return false;
  }
}
