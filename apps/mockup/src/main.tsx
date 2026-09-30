import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { MockStoreProvider } from './store/MockStore';
import { ThemeProvider } from './store/ThemeContext';
import './styles/tokens.css';
import './styles/globals.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <ThemeProvider>
        <MockStoreProvider>
          <App />
        </MockStoreProvider>
      </ThemeProvider>
    </BrowserRouter>
  </React.StrictMode>,
);
