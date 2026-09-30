import { FormEvent, useState } from 'react';
import { useMockStore } from '../../store/MockStore';
import { SEED_PERMISSION_PROFILES, SEED_ROLES } from '../../store/seed';
import modal from '../../styles/modal.module.css';
import shared from '../../styles/shared.module.css';

/** 프로젝트 상세에서 새 에이전트를 만든다. 런타임은 켜져 있는 것만 고를 수 있다. */
export default function NewAgentModal({ projectId, onClose }: { projectId: number; onClose: () => void }) {
  const { providers, createAgent } = useMockStore();
  const enabledProviders = providers.filter((p) => p.enabled);

  const [name, setName] = useState('');
  const [roleId, setRoleId] = useState('');
  const [permissionProfileId, setPermissionProfileId] = useState('');
  const [providerId, setProviderId] = useState('');
  const [persona, setPersona] = useState('');
  const [model, setModel] = useState('');
  const [mode, setMode] = useState('');

  const selectedProvider = enabledProviders.find((p) => String(p.id) === providerId) ?? null;
  const modelOptions = selectedProvider?.capabilities.models ?? [];
  const modeOptions = selectedProvider?.capabilities.modes ?? [];

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    createAgent({
      projectId,
      name: name.trim(),
      roleId: Number(roleId),
      permissionProfileId: Number(permissionProfileId),
      providerId: Number(providerId),
      persona: persona.trim(),
      model: model.trim(),
      mode: mode.trim(),
    });
    onClose();
  };

  return (
    <div className={modal.overlay} onClick={onClose}>
      <form className={`${modal.modal} ${modal.wide}`} onClick={(e) => e.stopPropagation()} onSubmit={onSubmit}>
        <h2>새 에이전트</h2>

        <div className={modal.field}>
          <label>이름</label>
          <input placeholder="예: Backend Developer A" value={name} onChange={(e) => setName(e.target.value)} required />
        </div>

        <div className={modal.field}>
          <label>Role</label>
          <select value={roleId} onChange={(e) => setRoleId(e.target.value)} required>
            <option value="">Role 선택</option>
            {SEED_ROLES.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
              </option>
            ))}
          </select>
        </div>

        <div className={modal.field}>
          <label>Permission Profile</label>
          <select value={permissionProfileId} onChange={(e) => setPermissionProfileId(e.target.value)} required>
            <option value="">Permission Profile 선택</option>
            {SEED_PERMISSION_PROFILES.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </div>

        <div className={modal.field}>
          <label>런타임 (켜 둔 것만)</label>
          <select value={providerId} onChange={(e) => setProviderId(e.target.value)} required>
            <option value="">런타임 선택</option>
            {enabledProviders.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} ({p.key})
              </option>
            ))}
          </select>
          {enabledProviders.length === 0 && (
            <span className={shared.hint}>켜져 있는 런타임이 없습니다. 설정에서 런타임을 켜세요.</span>
          )}
        </div>

        <div className={modal.field}>
          <label>페르소나 (선택)</label>
          <textarea
            rows={3}
            placeholder="성격/일하는 방식 — 시스템 프롬프트로 덧붙습니다"
            value={persona}
            onChange={(e) => setPersona(e.target.value)}
          />
        </div>

        {selectedProvider && (
          <p className={shared.hint}>
            {selectedProvider.name} 지원 목록 — 모델: {modelOptions.length > 0 ? modelOptions.join(', ') : '미등록'} / 모드:{' '}
            {modeOptions.length > 0 ? modeOptions.join(', ') : '미등록'}
          </p>
        )}

        <div className={modal.field}>
          <label>모델 (선택)</label>
          <input
            list="new-agent-model-options"
            placeholder="목록에서 고르거나 직접 입력"
            value={model}
            onChange={(e) => setModel(e.target.value)}
          />
          <datalist id="new-agent-model-options">
            {modelOptions.map((m) => (
              <option key={m} value={m} />
            ))}
          </datalist>
        </div>

        <div className={modal.field}>
          <label>모드 (선택)</label>
          <input
            list="new-agent-mode-options"
            placeholder="예: plan, acceptEdits"
            value={mode}
            onChange={(e) => setMode(e.target.value)}
          />
          <datalist id="new-agent-mode-options">
            {modeOptions.map((m) => (
              <option key={m} value={m} />
            ))}
          </datalist>
        </div>

        <div className={modal.actions}>
          <button type="button" onClick={onClose}>
            취소
          </button>
          <button type="submit" disabled={enabledProviders.length === 0}>
            만들기
          </button>
        </div>
      </form>
    </div>
  );
}
