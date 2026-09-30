import { FormEvent, useState } from 'react';
import { createPortal } from 'react-dom';
import { unavailableReason } from '../../lib/agentAvailability';
import { useMockStore } from '../../store/MockStore';
import { SEED_PERMISSION_PROFILES, SEED_ROLES } from '../../store/seed';
import modal from '../../styles/modal.module.css';
import shared from '../../styles/shared.module.css';
import type { Agent, Project } from '../../types';

/**
 * 에이전트 생성/수정 창. agent 를 주면 그 에이전트의 상세 설정 수정, 없으면 새 에이전트 생성이다.
 * 런타임은 켜져 있는 것만 고를 수 있다(수정 중인 에이전트의 현재 런타임은 꺼져 있어도 목록에 남긴다).
 */
export default function AgentFormModal({
  project,
  agent,
  onClose,
}: {
  project: Project;
  /** 있으면 수정 모드 */
  agent?: Agent;
  onClose: () => void;
}) {
  const { providers, createAgent, updateAgent } = useMockStore();
  const isEdit = agent !== undefined;
  const selectableProviders = providers.filter((p) => p.enabled || p.id === agent?.providerId);

  const [name, setName] = useState(agent?.name ?? '');
  const [roleId, setRoleId] = useState(agent ? String(agent.roleId) : '');
  const [permissionProfileId, setPermissionProfileId] = useState(agent ? String(agent.permissionProfileId) : '');
  const [providerId, setProviderId] = useState(agent ? String(agent.providerId) : '');
  const [persona, setPersona] = useState(agent?.persona ?? '');
  const [model, setModel] = useState(agent?.model ?? '');
  const [mode, setMode] = useState(agent?.mode ?? '');

  const selectedProvider = selectableProviders.find((p) => String(p.id) === providerId) ?? null;
  const modelOptions = selectedProvider?.capabilities.models ?? [];
  const modeOptions = selectedProvider?.capabilities.modes ?? [];
  const reason = agent ? unavailableReason(agent, providers, project) : null;
  const noRuntime = providers.filter((p) => p.enabled).length === 0;

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    const values = {
      name: name.trim(),
      roleId: Number(roleId),
      permissionProfileId: Number(permissionProfileId),
      providerId: Number(providerId),
      persona: persona.trim(),
      model: model.trim(),
      mode: mode.trim(),
    };
    if (agent) updateAgent(agent.id, values);
    else createAgent({ ...values, projectId: project.id });
    onClose();
  };

  // 왼쪽 패널(글래스/스크롤)에 갇히지 않도록 body 에 포털로 그린다.
  return createPortal(
    <div className={modal.overlay} onClick={onClose}>
      <form className={`${modal.modal} ${modal.wide}`} onClick={(e) => e.stopPropagation()} onSubmit={onSubmit}>
        <h2>{isEdit ? '에이전트 상세 설정' : '새 에이전트'}</h2>
        {reason && <p className="errorText">지금은 실행할 수 없습니다: {reason}</p>}

        <div className={modal.field}>
          <label>이름</label>
          <input placeholder="예: Backend Developer A" value={name} onChange={(e) => setName(e.target.value)} required />
        </div>

        <div className={modal.field}>
          <label>역할 (Role)</label>
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
            {selectableProviders.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} ({p.key}){p.enabled ? '' : ' — 꺼짐'}
              </option>
            ))}
          </select>
          {noRuntime && <span className={shared.hint}>켜져 있는 런타임이 없습니다. 설정에서 런타임을 켜세요.</span>}
        </div>

        <div className={modal.field}>
          <label>프롬프트 (페르소나, 선택)</label>
          <textarea
            rows={4}
            placeholder="성격/일하는 방식 — 이 에이전트의 시스템 프롬프트로 덧붙습니다"
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
            list="agent-form-model-options"
            placeholder="목록에서 고르거나 직접 입력"
            value={model}
            onChange={(e) => setModel(e.target.value)}
          />
          <datalist id="agent-form-model-options">
            {modelOptions.map((m) => (
              <option key={m} value={m} />
            ))}
          </datalist>
        </div>

        <div className={modal.field}>
          <label>모드 (선택)</label>
          <input
            list="agent-form-mode-options"
            placeholder="예: plan, acceptEdits"
            value={mode}
            onChange={(e) => setMode(e.target.value)}
          />
          <datalist id="agent-form-mode-options">
            {modeOptions.map((m) => (
              <option key={m} value={m} />
            ))}
          </datalist>
        </div>

        <div className={modal.actions}>
          <button type="button" onClick={onClose}>
            취소
          </button>
          <button type="submit" disabled={noRuntime && !isEdit}>
            {isEdit ? '저장' : '만들기'}
          </button>
        </div>
      </form>
    </div>,
    document.body,
  );
}
