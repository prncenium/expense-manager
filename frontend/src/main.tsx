import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import './index.css';
import { Layout } from './components/Layout';
import { ToastProvider } from './components/Toasts';
import { DashboardPage } from './pages/DashboardPage';
import { ExpensesPage } from './pages/ExpensesPage';
import { UploadPage } from './pages/UploadPage';
import { RulesPage } from './pages/RulesPage';
import { ComparePage } from './pages/ComparePage';
import { MuneemPage } from './pages/MuneemPage';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <ToastProvider>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<DashboardPage />} />
            <Route path="compare" element={<ComparePage />} />
            <Route path="expenses" element={<ExpensesPage />} />
            <Route path="upload" element={<UploadPage />} />
            <Route path="rules" element={<RulesPage />} />
            <Route path="muneem" element={<MuneemPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </ToastProvider>
    </BrowserRouter>
  </StrictMode>,
);
