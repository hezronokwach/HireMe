import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideApi } from '../../api';
import { AssistantChat, MAX_MESSAGES, toRequest } from './assistant-chat.service';

const URL = '/api/v1/equipment/ai/assistant';

describe('AssistantChat', () => {
  let http: HttpTestingController;

  function setUp(): AssistantChat {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi('')],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(AssistantChat);
  }

  beforeEach(() => sessionStorage.clear());
  afterEach(() => http.verify());

  it('sends the whole conversation, with the listings each answer showed', () => {
    const chat = setUp();
    chat.send('  a tractor in Nakuru ');
    const first = http.expectOne(URL);
    expect(first.request.body).toEqual({
      messages: [{ role: 'USER', text: 'a tractor in Nakuru' }],
    });
    first.flush({
      reply: 'Two tractors.',
      listings: [{ id: 1 }, { id: 2 }],
      suggestions: ['Cheaper?'],
    });
    expect(chat.sending()).toBe(false);

    chat.send('the second one?');
    expect(http.expectOne(URL).request.body.messages).toEqual([
      { role: 'USER', text: 'a tractor in Nakuru' },
      { role: 'ASSISTANT', text: 'Two tractors.', listingIds: [1, 2] },
      { role: 'USER', text: 'the second one?' },
    ]);
  });

  it('keeps the conversation for the tab, so leaving the page does not lose it', () => {
    let chat = setUp();
    chat.send('hello');
    http.expectOne(URL).flush({ reply: 'Hi! What do you need?', listings: [] });

    TestBed.resetTestingModule();
    chat = setUp();
    expect(chat.entries().map((e) => e.text)).toEqual(['hello', 'Hi! What do you need?']);

    chat.reset();
    expect(chat.entries()).toEqual([]);
    expect(JSON.parse(sessionStorage.getItem('hireme.assistant') ?? '[]')).toEqual([]);
  });

  it('ignores blank messages and messages sent while waiting', () => {
    const chat = setUp();
    chat.send('   ');
    http.expectNone(URL);

    chat.send('one');
    chat.send('two');
    http.expectOne(URL).flush({ reply: 'ok' });
    expect(chat.entries().map((e) => e.text)).toEqual(['one', 'ok']);
  });

  it('explains errors and can send the failed message again', () => {
    const chat = setUp();
    chat.send('tractor');
    http.expectOne(URL).flush({ message: 'down' }, { status: 503, statusText: 'Unavailable' });
    expect(chat.error()).toContain('You can still use the filters');
    expect(chat.canRetry()).toBe(true);

    chat.retry();
    const again = http.expectOne(URL);
    expect(again.request.body.messages).toEqual([{ role: 'USER', text: 'tractor' }]);
    again.flush(
      { message: 'Too many AI requests. Try again in 30 seconds.' },
      { status: 429, statusText: 'Too Many' },
    );
    expect(chat.error()).toBe('Too many AI requests. Try again in 30 seconds.');
  });

  it('sends back the days each answer was about, so follow-ups keep them', () => {
    const request = toRequest([
      { role: 'USER', text: 'tractor next weekend' },
      {
        role: 'ASSISTANT',
        text: 'One.',
        listings: [{ id: 1 }],
        from: '2030-01-26',
        to: '2030-01-27',
      },
      { role: 'USER', text: 'driver?' },
    ]);
    expect(request.messages[1]).toEqual({
      role: 'ASSISTANT',
      text: 'One.',
      listingIds: [1],
      from: '2030-01-26',
      to: '2030-01-27',
    });
    expect(request.messages[0].from).toBeUndefined();
  });

  it('sends at most the last 20 messages', () => {
    const long = Array.from({ length: 30 }, (_, i) => ({
      role: (i % 2 ? 'ASSISTANT' : 'USER') as 'USER' | 'ASSISTANT',
      text: `m${i}`,
    }));
    const request = toRequest(long);
    expect(request.messages.length).toBe(MAX_MESSAGES);
    expect(request.messages.at(-1)?.text).toBe('m29');
  });
});
