// calculator.component.ts
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-calculator',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatButtonModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './calculator.component.html',
  styleUrls: ['./calculator.component.scss']
})
export class CalculatorComponent {
  file: File | null = null;
  results: any = null;
  error = '';
  loading = false;

  constructor(private http: HttpClient) {}

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.file = input.files[0];
      this.results = null;
      this.error = '';
    }
  }

  uploadAndCalculate(): void {
    if (!this.file) {
      this.error = 'Seleziona un file STL prima di procedere.';
      return;
    }
    const formData = new FormData();
    formData.append('file', this.file);
    this.loading = true;
    this.http.post<any>('http://localhost:8000/calculate/stl', formData)
      .subscribe({
        next: res => {
          this.results = res;
          this.loading = false;
        },
        error: err => {
          this.error = err.error?.detail || err.message;
          this.loading = false;
        }
      });
  }
}
