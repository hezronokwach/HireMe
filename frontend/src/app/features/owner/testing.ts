import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';

/** A MatDialog stand-in whose dialogs close with `answer` and remember what they asked. */
export function fakeDialog(answer = true) {
  const asked: { title: string; message: string }[] = [];
  const dialog = {
    asked,
    answer,
    open: (_component: unknown, config: { data: { title: string; message: string } }) => {
      asked.push(config.data);
      return { afterClosed: () => of(dialog.answer) };
    },
  };
  return dialog;
}

/** A MatSnackBar stand-in that keeps the messages it was asked to show. */
export function fakeSnackBar() {
  const messages: string[] = [];
  return {
    messages,
    open: (message: string) => {
      messages.push(message);
      return {};
    },
  };
}

export function dialogProviders(
  dialog: ReturnType<typeof fakeDialog>,
  snackBar: ReturnType<typeof fakeSnackBar>,
) {
  return [
    { provide: MatDialog, useValue: dialog },
    { provide: MatSnackBar, useValue: snackBar },
  ];
}
