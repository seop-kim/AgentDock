import { DragEvent, FormEvent, useEffect, useRef, useState } from 'react';
import { ChevronDownIcon, ClipIcon, CollapseIcon, ExpandIcon, FileIcon } from '../../components/icons';
import { unavailableReason } from '../../lib/agentAvailability';
import { ATTACHMENT_FOLDER, attachmentPath, hasAttachment, storedName } from '../../lib/attachments';
import { useMockStore } from '../../store/MockStore';
import attach from '../../styles/attachment.module.css';
import shared from '../../styles/shared.module.css';
import type { AttachedFile, ChatTarget, Project } from '../../types';
import AttachFilesModal from './AttachFilesModal';
import styles from './ChatPanel.module.css';
import { ExecutionSummaryCard } from './ExecutionTree';

const toValue = (target: ChatTarget | null) => (target ? `${target.kind}:${target.id}` : '');

const fromValue = (value: string): ChatTarget | null => {
  const [kind, id] = value.split(':');
  return kind === 'agent' || kind === 'group' ? { kind, id: Number(id) } : null;
};

/**
 * 구성도 아래에 떠 있는 채팅 창. 기본 대상은 프로젝트 **마스터 에이전트**이고, 원하면 그룹(리더가 받음)이나
 * 개별 에이전트를 직접 고를 수도 있다. 마스터에게 보내면 마스터가 팀 리더들에게 나눠 맡긴다(모의 응답).
 */
export default function ChatPanel({
  project,
  target,
  onTargetChange,
  open,
  onToggle,
  wide,
  onToggleWide,
  onOpenExecutions,
}: {
  project: Project;
  target: ChatTarget | null;
  onTargetChange: (target: ChatTarget | null) => void;
  /** 패널을 펼쳐 두었는지(접으면 머리말만 남는다). */
  open: boolean;
  onToggle: () => void;
  /** 크게 보기(일시적으로 구성도를 덮고 넓게 쓴다). */
  wide: boolean;
  onToggleWide: () => void;
  /** 응답에 딸린 실행 트리를 연다. */
  onOpenExecutions: (rootExecutionId: number) => void;
}) {
  const { agents, groups, providers, chats, executions, sendCommand, addWorkspaceFile } = useMockStore();
  const [text, setText] = useState('');
  /** 이번 명령에 붙일 파일(보내면 비운다). */
  const [attachments, setAttachments] = useState<AttachedFile[]>([]);
  const [pickerOpen, setPickerOpen] = useState(false);
  /** 파일을 끌어오는 중인지(놓을 곳을 보여 준다). */
  const [dragging, setDragging] = useState(false);
  const logRef = useRef<HTMLDivElement>(null);

  const projectAgents = agents.filter((a) => a.projectId === project.id);
  const projectGroups = groups.filter((g) => g.projectId === project.id);
  const projectExecutions = executions.filter((e) => e.projectId === project.id);
  const messages = chats.filter((m) => m.projectId === project.id);

  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight });
  }, [messages.length, messages.filter((m) => m.status === 'pending').length, open]);

  const receiverHint = (() => {
    if (!target) return '명령을 받을 에이전트나 그룹을 고르세요.';
    if (target.kind === 'agent') {
      const agent = projectAgents.find((a) => a.id === target.id);
      if (!agent) return null;
      const reason = unavailableReason(agent, providers, project);
      if (reason) return `${agent.name}: ${reason}`;
      return agent.id === project.masterAgentId
        ? `${agent.name}(마스터)가 받아 팀(그룹) 리더들에게 나눠 맡깁니다.`
        : null;
    }
    const group = projectGroups.find((g) => g.id === target.id);
    const leader = projectAgents.find((a) => a.id === group?.leaderAgentId);
    if (!leader) return `${group?.name} 에는 리더가 없습니다.`;
    const reason = unavailableReason(leader, providers, project);
    return reason ? `리더 ${leader.name}: ${reason}` : `리더 ${leader.name} 이(가) 받습니다.`;
  })();

  /** 끌어온 파일을 복사해 둘 워크스페이스(기본 워크스페이스, 없으면 첫 번째). */
  const defaultWorkspaceId =
    project.workspaces.find((w) => w.isDefault)?.workspaceId ?? project.workspaces[0]?.workspaceId ?? null;

  /**
   * 창 밖에서 끌어온 파일을 붙인다. 에이전트는 워크스페이스 폴더 밖을 볼 수 없으므로
   * 프로젝트 안 폴더(`ATTACHMENT_FOLDER`)로 **복사한 것으로 치고**, 그 사본 경로를 첨부로 단다.
   * 폴더는 받지 않는다(파일만).
   */
  const onDrop = (e: DragEvent<HTMLElement>) => {
    e.preventDefault();
    setDragging(false);
    if (defaultWorkspaceId === null) return;
    const entries = Array.from(e.dataTransfer.items).map((item) => item.webkitGetAsEntry());
    const dropped = Array.from(e.dataTransfer.files).filter((_, index) => entries[index]?.isDirectory !== true);
    if (dropped.length === 0) return;
    // 같은 이름을 여러 번 붙여도 충돌하지 않게 **저장 이름 앞에 id(uuid)를 붙인다**(원래 이름은 화면에 남긴다).
    const copies = dropped.map((file) => {
      const path = attachmentPath(storedName(file.name));
      addWorkspaceFile(defaultWorkspaceId, path);
      return { workspaceId: defaultWorkspaceId, path, name: file.name };
    });
    setAttachments((prev) => [...prev, ...copies.filter((copy) => !hasAttachment(prev, copy))]);
  };

  const onDragOver = (e: DragEvent<HTMLElement>) => {
    e.preventDefault();
    setDragging(true);
  };

  const onDragLeave = (e: DragEvent<HTMLElement>) => {
    // 자식 요소로 옮겨 다닐 때도 dragleave 가 오므로, 창 밖으로 나갈 때만 끈다.
    if (e.currentTarget.contains(e.relatedTarget as Node | null)) return;
    setDragging(false);
  };

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!target) return;
    const trimmed = text.trim();
    // 파일만 붙이고 보내도 되게 한다(그때는 지시를 기본 문장으로 채운다).
    if (trimmed === '' && attachments.length === 0) return;
    sendCommand(project.id, target, trimmed === '' ? '첨부한 파일을 확인해줘' : trimmed, attachments);
    setText('');
    setAttachments([]);
  };

  return (
    <section
      className={`${styles.chat} ${wide ? styles.wide : ''} ${open ? '' : styles.collapsed} ${
        dragging ? styles.dropTarget : ''
      }`}
      aria-label="에이전트 명령"
      onDragOver={onDragOver}
      onDragLeave={onDragLeave}
      onDrop={onDrop}
    >
      {/* 왼쪽 가장자리 가운데에 살짝 튀어나온 크게 보기 버튼 */}
      {open && (
        <button
          type="button"
          className={styles.wideButton}
          onClick={onToggleWide}
          aria-label={wide ? '원래 크기로' : '크게 보기'}
          title={wide ? '원래 크기로' : '크게 보기'}
        >
          {wide ? <CollapseIcon size={16} /> : <ExpandIcon size={16} />}
        </button>
      )}

      <div className={styles.header}>
        <button
          type="button"
          className={styles.fold}
          onClick={onToggle}
          aria-expanded={open}
          aria-label={open ? '명령 패널 접기' : '명령 패널 펴기'}
          title={open ? '명령 패널 접기' : '명령 패널 펴기'}
        >
          <ChevronDownIcon />
        </button>
        <h2 className={styles.title}>명령</h2>
        {!open && messages.length > 0 && <span className={styles.count}>기록 {messages.length}</span>}
      </div>

      {open && (
        <>
        <select
          className={styles.target}
          value={toValue(target)}
          onChange={(e) => onTargetChange(fromValue(e.target.value))}
          aria-label="명령 대상"
        >
          <option value="">대상 선택</option>
          <optgroup label="에이전트">
            {projectAgents.map((a) => (
              <option key={a.id} value={`agent:${a.id}`}>
                {a.name}
                {a.id === project.masterAgentId ? ' (마스터)' : ''}
              </option>
            ))}
          </optgroup>
          <optgroup label="그룹 (리더가 받음)">
            {projectGroups.map((g) => (
              <option key={g.id} value={`group:${g.id}`}>
                {g.name}
              </option>
            ))}
          </optgroup>
        </select>
        {receiverHint && <span className={styles.hint}>{receiverHint}</span>}

        <div ref={logRef} className={styles.log}>
          {messages.length === 0 && <p className={shared.muted}>아직 보낸 명령이 없습니다.</p>}
          {messages.map((m) => {
            const rootId = m.rootExecutionId;
            return (
              <div key={m.id} className={`${styles.message} ${styles[m.role]}`}>
                <div className={styles.meta}>
                  <strong>{m.author}</strong>
                  {m.targetLabel && <span> → {m.targetLabel}</span>}
                </div>
                <div className={m.status === 'error' ? styles.error : undefined}>
                  {m.status === 'pending' ? <span className={styles.pending}>작업 중…</span> : m.text}
                </div>
                {m.attachments.length > 0 && (
                  <ul className={attach.chips}>
                    {m.attachments.map((file) => (
                      <li key={`${file.workspaceId}:${file.path}`} className={attach.chip} title={file.path}>
                        <FileIcon size={13} />
                        <span className={attach.chipName}>{file.name}</span>
                      </li>
                    ))}
                  </ul>
                )}
                {rootId !== null && (
                  <ExecutionSummaryCard
                    executions={projectExecutions}
                    rootExecutionId={rootId}
                    onOpen={() => onOpenExecutions(rootId)}
                  />
                )}
              </div>
            );
          })}
        </div>

      {attachments.length > 0 && (
        <ul className={attach.chips}>
          {attachments.map((file) => (
            <li key={`${file.workspaceId}:${file.path}`} className={attach.chip} title={file.path}>
              <FileIcon size={13} />
              <span className={attach.chipName}>{file.name}</span>
              <button
                type="button"
                className={attach.chipRemove}
                onClick={() => setAttachments((prev) => prev.filter((item) => item !== file))}
                aria-label={`${file.name} 첨부 빼기`}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}

      {dragging && (
        <p className={styles.dropHint}>
          {defaultWorkspaceId === null ? (
            '워크스페이스가 없어 파일을 붙일 수 없습니다.'
          ) : (
            <>
              여기에 놓으면 프로젝트 폴더(<code>{ATTACHMENT_FOLDER}</code>)로 복사해 붙입니다.
            </>
          )}
        </p>
      )}

      <form onSubmit={onSubmit} className={styles.form}>
        <button
          type="button"
          className={styles.attach}
          onClick={() => setPickerOpen(true)}
          disabled={project.workspaces.length === 0}
          aria-label="파일 첨부"
          title={
            project.workspaces.length === 0
              ? '워크스페이스가 없어 파일을 붙일 수 없습니다'
              : '파일 첨부 (창에 끌어다 놓아도 됩니다)'
          }
        >
          <ClipIcon size={18} />
        </button>
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={target ? '명령을 입력하고 Enter' : '먼저 대상을 선택하세요'}
          aria-label="명령 입력"
        />
        <button type="submit" disabled={!target || (text.trim() === '' && attachments.length === 0)}>
          보내기
        </button>
        </form>
        </>
      )}

      {pickerOpen && (
        <AttachFilesModal
          project={project}
          attached={attachments}
          onClose={() => setPickerOpen(false)}
          onApply={(files) => {
            setAttachments(files);
            setPickerOpen(false);
          }}
        />
      )}
    </section>
  );
}
