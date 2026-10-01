import { useState } from 'react';
import { useMockStore } from '../../store/MockStore';
import shared from '../../styles/shared.module.css';
import type { AiProvider } from '../../types';
import CliPanel from './CliPanel';
import styles from './Settings.module.css';

// 목록이 자주 바뀌므로 화면에서 직접 갱신한다(코드 수정 불필요). 줄바꿈 또는 콤마로 구분한다.
const toLines = (values?: string[] | null) => (values ?? []).join('\n');

const parseLines = (text: string) =>
  Array.from(new Set(text.split(/[\n,]/).map((value) => value.trim()).filter((value) => value !== '')));

/** 설치 단계는 한 줄에 하나씩(순서대로 실행). */
const parseSteps = (text: string) =>
  text.split('\n').map((line) => line.trim()).filter((line) => line !== '' && !line.startsWith('#'));

export default function RuntimeCard({ provider }: { provider: AiProvider }) {
  const { toggleProvider, deleteProvider, updateCapabilities } = useMockStore();
  const [cliOpen, setCliOpen] = useState(false);
  const [capabilitiesOpen, setCapabilitiesOpen] = useState(false);
  const [modelsText, setModelsText] = useState('');
  const [modesText, setModesText] = useState('');
  const [notesText, setNotesText] = useState('');
  const [installText, setInstallText] = useState('');
  const [requireText, setRequireText] = useState('');
  const [prerequisiteText, setPrerequisiteText] = useState('');

  const { models, modes, install: installSteps } = provider.capabilities;

  const onDelete = () => {
    if (!window.confirm('이 런타임을 쓰는 에이전트는 사용 불가가 되며 다른 런타임을 다시 할당해야 합니다. 삭제할까요?')) return;
    deleteProvider(provider.id);
  };

  const openCapabilities = () => {
    const c = provider.capabilities;
    setModelsText(toLines(c.models));
    setModesText(toLines(c.modes));
    setNotesText(c.notes ?? '');
    setInstallText(c.install.join('\n'));
    setRequireText(c.installRequire ?? '');
    setPrerequisiteText(c.installPrerequisite.join('\n'));
    setCapabilitiesOpen(true);
  };

  const onSaveCapabilities = () => {
    updateCapabilities(provider.id, {
      models: parseLines(modelsText),
      modes: parseLines(modesText),
      notes: notesText.trim() === '' ? null : notesText.trim(),
      install: parseSteps(installText),
      installRequire: requireText.trim() === '' ? null : requireText.trim(),
      installPrerequisite: parseSteps(prerequisiteText),
    });
    setCapabilitiesOpen(false);
  };

  return (
    <div className={styles.card}>
      <div className={styles.cardHeader}>
        <h2 className={styles.cardTitle}>
          {provider.name} <span className={shared.muted}>({provider.key})</span>
        </h2>
        <button className={shared.dangerButton} onClick={onDelete}>
          런타임 삭제
        </button>
      </div>

      <div className={shared.statusRow}>
        <span className={`${shared.badge} ${provider.enabled ? shared.badgeOk : shared.badgeError}`}>
          {provider.enabled ? 'ON' : 'OFF'}
        </span>
        <span className={shared.muted}>
          {provider.enabled ? '켜짐 — 워크스페이스에서 폴더별 상태를 확인하세요' : '꺼짐 — 할당·실행에 쓰이지 않습니다'}
        </span>
      </div>

      <div className={shared.actions}>
        <button onClick={() => toggleProvider(provider.id)}>{provider.enabled ? '끄기' : '켜기'}</button>
        <button onClick={() => setCliOpen(true)}>CLI 확인</button>
      </div>
      {installSteps.length === 0 && (
        <p className={shared.muted}>
          설치 명령이 비어 있습니다("모델/모드/설치 편집"에서 입력하면 CLI 확인 창의 설치 버튼이 켜집니다).
        </p>
      )}
      {cliOpen && <CliPanel provider={provider} onClose={() => setCliOpen(false)} />}

      <p className={shared.muted}>
        모델: {models.length > 0 ? models.join(', ') : '미등록'} / 모드: {modes.length > 0 ? modes.join(', ') : '미등록'}
      </p>
      <div className={shared.actions}>
        <button onClick={() => (capabilitiesOpen ? setCapabilitiesOpen(false) : openCapabilities())}>
          {capabilitiesOpen ? '설정 닫기' : '모델/모드/설치 편집'}
        </button>
      </div>
      {capabilitiesOpen && (
        <div className={`${shared.panel} ${styles.editor}`}>
          <p className={shared.hint}>
            목록은 코드가 아니라 데이터입니다. 모델/모드는 줄바꿈 또는 콤마로, 설치 단계는 한 줄에 하나씩 입력합니다.
            설치 단계는 셸을 통해 순서대로 실행되며(.cmd/.ps1 및 PATH 사용), 필수 도구가 없으면 "필수 도구 설치" 단계를
            먼저 실행합니다.
          </p>
          <label className={shared.muted}>모델</label>
          <textarea rows={3} value={modelsText} onChange={(e) => setModelsText(e.target.value)} placeholder={'opus\nsonnet'} />
          <label className={shared.muted}>모드 (CLI 의 실행/권한 모드)</label>
          <textarea rows={3} value={modesText} onChange={(e) => setModesText(e.target.value)} placeholder={'plan\nacceptEdits'} />
          <label className={shared.muted}>CLI 설치 단계 (한 줄에 하나, 순서대로)</label>
          <textarea
            rows={2}
            value={installText}
            onChange={(e) => setInstallText(e.target.value)}
            placeholder="npm install -g @anthropic-ai/claude-code"
          />
          <label className={shared.muted}>필수 도구 (없으면 아래 단계를 먼저 실행)</label>
          <input value={requireText} onChange={(e) => setRequireText(e.target.value)} placeholder="예: npm" />
          <label className={shared.muted}>필수 도구 설치 단계 (한 줄에 하나)</label>
          <textarea
            rows={2}
            value={prerequisiteText}
            onChange={(e) => setPrerequisiteText(e.target.value)}
            placeholder={'nvm install lts\nnvm use lts'}
          />
          <label className={shared.muted}>메모 (선택)</label>
          <input value={notesText} onChange={(e) => setNotesText(e.target.value)} placeholder="예: CLI 버전에 따라 바뀜" />
          <div className={shared.actions}>
            <button onClick={onSaveCapabilities}>저장</button>
          </div>
        </div>
      )}
    </div>
  );
}
