import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { AgentDockStoreProvider } from './store/AgentDockStore';
import { ThemeProvider } from './store/ThemeContext';
import './styles/tokens.css';
import './styles/globals.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <ThemeProvider>
        <AgentDockStoreProvider>
          <App />
        </AgentDockStoreProvider>
      </ThemeProvider>
    </BrowserRouter>
  </React.StrictMode>,
);
