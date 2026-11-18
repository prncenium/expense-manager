import { useRef, useState, type DragEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage, NetworkError } from '../api/client';
import { useToast } from '../components/Toasts';
import { Button, Card, PageHeader } from '../components/ui';
import type { UploadResult } from '../types';

const MAX_BYTES = 2 * 1024 * 1024;

export function UploadPage() {
  const toast = useToast();
  const inputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [result, setResult] = useState<UploadResult | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);

  function choose(f: File | undefined) {
    setResult(null);
    setUploadError(null);
    if (!f) return;
    if (!f.name.toLowerCase().endsWith('.csv')) {
      setFile(null);
      setFileError('Only .csv files are accepted');
    } else if (f.size > MAX_BYTES) {
      setFile(null);
      setFileError('File is too large (max 2 MB)');
    } else {
      setFile(f);
      setFileError(null);
    }
  }

  function onDrop(e: DragEvent) {
    e.preventDefault();
    setDragging(false);
    choose(e.dataTransfer.files[0]);
  }

  async function upload() {
    if (!file) return;
    setUploading(true);
    setUploadError(null);
    try {
      const r = await api.upload(file);
      setResult(r);
      toast.success(`${r.saved} row${r.saved === 1 ? '' : 's'} imported`);
      setFile(null);
      if (inputRef.current) inputRef.current.value = '';
    } catch (e) {
      if (!(e instanceof NetworkError)) setUploadError(errorMessage(e));
    } finally {
      setUploading(false);
    }
  }

  return (
    <>
      <PageHeader title="Upload CSV">
        <a href="/expenses.csv" download="sample-expenses.csv" className="text-sm font-medium text-indigo-600 hover:underline">
          ↓ Download sample CSV
        </a>
      </PageHeader>

      <div className="grid gap-6 lg:grid-cols-2 lg:items-start">
        <Card>
          <label
            htmlFor="csv-file"
            onDragOver={(e) => {
              e.preventDefault();
              setDragging(true);
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={onDrop}
            className={`flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed px-4 py-10 text-center transition-colors ${
              dragging ? 'border-indigo-500 bg-indigo-50' : 'border-slate-300 hover:border-indigo-400 hover:bg-slate-50'
            }`}
          >
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="mb-2 text-slate-400" aria-hidden="true">
              <path d="M12 16V4m0 0l-4 4m4-4l4 4M4 16v2a2 2 0 002 2h12a2 2 0 002-2v-2" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            <span className="text-sm font-medium text-slate-700">
              {file ? file.name : 'Drop a .csv file here, or click to choose'}
            </span>
            <span className="mt-1 text-xs text-slate-500">
              {file ? `${(file.size / 1024).toFixed(1)} KB` : 'Max 2 MB · up to 5,000 rows'}
            </span>
            <input
              ref={inputRef}
              id="csv-file"
              type="file"
              accept=".csv,text/csv"
              className="sr-only"
              onChange={(e) => choose(e.target.files?.[0])}
            />
          </label>
          {fileError && <p className="mt-2 text-sm text-red-600">{fileError}</p>}

          <Button className="mt-4 w-full" disabled={!file} busy={uploading} onClick={upload}>
            {uploading ? 'Uploading…' : 'Upload'}
          </Button>

          <div className="mt-5 text-xs leading-relaxed text-slate-500">
            <p className="font-medium text-slate-600">Format</p>
            <p>
              Header row required: <code className="rounded bg-slate-100 px-1">date,amount,vendor,description</code> (any order,
              description optional). Dates as <code>YYYY-MM-DD</code> or <code>DD-MM-YYYY</code>. Bad rows and duplicates are
              skipped and listed; good rows are saved.
            </p>
          </div>
        </Card>

        <div className="min-w-0 space-y-4">
          {uploadError && (
            <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              <p className="font-medium">Upload failed, nothing was saved</p>
              <p className="mt-0.5">{uploadError}</p>
            </div>
          )}
          {result && <ResultView result={result} />}
          {!result && !uploadError && (
            <Card className="text-sm text-slate-500">The import summary will appear here after you upload a file.</Card>
          )}
        </div>
      </div>
    </>
  );
}

export function ResultView({ result }: { result: UploadResult }) {
  const invalid = result.skipped - result.duplicates;
  return (
    <Card>
      <h2 className="mb-4 text-base font-semibold text-slate-900">Import summary</h2>
      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Stat label="Rows" value={result.total} />
        <Stat label="Saved" value={result.saved} tone="green" />
        <Stat label="Skipped" value={result.skipped} tone={result.skipped ? 'amber' : undefined} />
        <Stat label="Duplicates" value={result.duplicates} />
      </dl>
      {result.skipped > 0 && (
        <p className="mt-2 text-xs text-slate-500">
          Skipped = {invalid} invalid + {result.duplicates} duplicate{result.duplicates === 1 ? '' : 's'}.
        </p>
      )}

      {result.anomaliesFound > 0 ? (
        <Link
          to="/expenses?anomaliesOnly=true"
          className="mt-4 flex items-center justify-between gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700 hover:bg-red-100"
        >
          <span>
            ⚠ {result.anomaliesFound} high expense{result.anomaliesFound === 1 ? '' : 's'} found
          </span>
          <span aria-hidden="true">View →</span>
        </Link>
      ) : (
        <p className="mt-4 text-sm text-slate-500">No high expenses found in the saved rows.</p>
      )}

      {result.errors.length > 0 && (
        <div className="mt-5">
          <h3 className="mb-2 text-sm font-semibold text-slate-700">Skipped rows</h3>
          <div className="max-h-80 overflow-auto rounded-lg border border-slate-200">
            <table className="w-full text-left text-sm">
              <thead className="sticky top-0 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="w-20 px-3 py-2 font-medium">Row</th>
                  <th className="px-3 py-2 font-medium">Reason</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {result.errors.map((e, i) => (
                  <tr key={`${e.row}-${i}`}>
                    <td className="px-3 py-2 tabular-nums text-slate-600">{e.row}</td>
                    <td className={`break-words px-3 py-2 ${e.reason === 'duplicate' ? 'text-slate-500' : 'text-red-700'}`}>{e.reason}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="mt-1 text-xs text-slate-400">Row numbers are file lines (the header is row 1).</p>
        </div>
      )}
    </Card>
  );
}

function Stat({ label, value, tone }: { label: string; value: number; tone?: 'green' | 'amber' }) {
  const color = tone === 'green' ? 'text-emerald-700' : tone === 'amber' ? 'text-amber-700' : 'text-slate-900';
  return (
    <div className="rounded-lg bg-slate-50 px-3 py-2">
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className={`text-xl font-semibold tabular-nums ${color}`}>{value}</dd>
    </div>
  );
}
