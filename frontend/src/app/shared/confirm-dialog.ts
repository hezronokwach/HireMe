import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { Observable, map } from 'rxjs';

export interface ConfirmOptions {
  title: string;
  message: string;
  /** Label of the button that goes ahead, e.g. "Delete". */
  confirm: string;
  /** Styles the confirm button as destructive. */
  danger?: boolean;
}

/** A yes/no question before something that can't be undone. */
@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>{{ data.message }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false">Keep it</button>
      <button
        mat-flat-button
        type="button"
        [class.danger]="data.danger"
        [mat-dialog-close]="true"
        cdkFocusInitial
      >
        {{ data.confirm }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .danger {
      --mat-button-filled-container-color: var(--mat-sys-error);
      --mat-button-filled-label-text-color: var(--mat-sys-on-error);
    }
  `,
})
export class ConfirmDialog {
  protected readonly data = inject<ConfirmOptions>(MAT_DIALOG_DATA);
}

/** Opens the dialog; emits true only when the user confirmed. */
export function confirmAction(dialog: MatDialog, options: ConfirmOptions): Observable<boolean> {
  return dialog
    .open<ConfirmDialog, ConfirmOptions, boolean>(ConfirmDialog, { data: options, width: '400px' })
    .afterClosed()
    .pipe(map((result) => result === true));
}
