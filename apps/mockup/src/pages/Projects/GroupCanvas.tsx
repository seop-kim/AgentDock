import {
  CSSProperties,
  DragEvent,
  FormEvent,
  PointerEvent,
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { unavailableReason } from '../../lib/agentAvailability';
import { layoutCanvas } from '../../lib/canvasLayout';
import { isAgentDrag, readAgentDrag, startAgentDrag } from '../../lib/dnd';
import { useMockStore } from '../../store/MockStore';
import { SEED_ROLES } from '../../store/seed';
import shared from '../../styles/shared.module.css';
import type { Project } from '../../types';
import styles from './GroupCanvas.module.css';

interface View {
  x: number;
  y: number;
  scale: number;
}

const MIN_SCALE = 0.3;
const MAX_SCALE = 2;
const FIT_MARGIN = 48;
const ZOOM_STEP = 1.2;

const clampScale = (scale: number) => Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale));

/** CSS 변수로 위치/크기를 넘긴다. 모양은 전부 GroupCanvas.module.css 가 정한다. */
const vars = (values: Record<string, string | number>) => values as unknown as CSSProperties;

/**
 * 프로젝트의 에이전트와 그룹을 보여 주는 캔버스. 그룹은 상자, 에이전트는 노드로 그리고 리더에서 멤버로 선을 잇는다.
 * 그룹에 속하지 않은 에이전트는 상자 없이 노드만 놓인다. 배경을 끌어 이동하고 휠로 확대/축소한다.
 * 에이전트 노드를 그룹 상자로 끌어 놓으면 멤버가 되고, 왼쪽 목록으로 끌면 그룹에서 빠진다.
 */
export default function GroupCanvas({ project, onNotice }: { project: Project; onNotice: (message: string) => void }) {
  const { agents, groups, providers, createGroup, deleteGroup, addGroupMember, removeGroupMember, setGroupLeader } =
    useMockStore();
  const projectAgents = useMemo(() => agents.filter((a) => a.projectId === project.id), [agents, project.id]);
  const projectGroups = useMemo(() => groups.filter((g) => g.projectId === project.id), [groups, project.id]);
  const layout = useMemo(() => layoutCanvas(projectAgents, projectGroups), [projectAgents, projectGroups]);

  const [name, setName] = useState('');
  const [view, setView] = useState<View>({ x: 0, y: 0, scale: 1 });
  const [overKey, setOverKey] = useState<string | null>(null);
  const viewportRef = useRef<HTMLDivElement>(null);
  const panRef = useRef<{ px: number; py: number; vx: number; vy: number } | null>(null);
  // 사용자가 직접 이동/확대하기 전까지는 내용이 바뀔 때마다 화면에 맞춘다.
  const touchedRef = useRef(false);

  const fit = useCallback(() => {
    const el = viewportRef.current;
    if (!el) return;
    const { width, height } = el.getBoundingClientRect();
    if (layout.width === 0) {
      setView({ x: 0, y: 0, scale: 1 });
      return;
    }
    const scale = Math.min(1, (width - FIT_MARGIN * 2) / layout.width, (height - FIT_MARGIN * 2) / layout.height);
    setView({
      scale,
      x: (width - layout.width * scale) / 2,
      y: Math.max(FIT_MARGIN, (height - layout.height * scale) / 2),
    });
  }, [layout.width, layout.height]);

  useLayoutEffect(() => {
    if (!touchedRef.current) fit();
  }, [fit]);

  // 휠은 페이지 스크롤이 아니라 캔버스 확대/축소로 쓴다(preventDefault 가 필요해 passive 로 등록하지 않는다).
  useEffect(() => {
    const el = viewportRef.current;
    if (!el) return;
    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      touchedRef.current = true;
      const rect = el.getBoundingClientRect();
      const cx = e.clientX - rect.left;
      const cy = e.clientY - rect.top;
      setView((prev) => {
        const scale = clampScale(prev.scale * Math.exp(-e.deltaY * 0.0015));
        const ratio = scale / prev.scale;
        return { scale, x: cx - (cx - prev.x) * ratio, y: cy - (cy - prev.y) * ratio };
      });
    };
    el.addEventListener('wheel', onWheel, { passive: false });
    return () => el.removeEventListener('wheel', onWheel);
  }, []);

  const zoomBy = (factor: number) => {
    const el = viewportRef.current;
    if (!el) return;
    touchedRef.current = true;
    const { width, height } = el.getBoundingClientRect();
    setView((prev) => {
      const scale = clampScale(prev.scale * factor);
      const ratio = scale / prev.scale;
      return { scale, x: width / 2 - (width / 2 - prev.x) * ratio, y: height / 2 - (height / 2 - prev.y) * ratio };
    });
  };

  const onPointerDown = (e: PointerEvent<HTMLDivElement>) => {
    if (e.button !== 0) return;
    if ((e.target as HTMLElement).closest('button, input, [draggable="true"]')) return;
    panRef.current = { px: e.clientX, py: e.clientY, vx: view.x, vy: view.y };
    e.currentTarget.setPointerCapture(e.pointerId);
  };

  const onPointerMove = (e: PointerEvent<HTMLDivElement>) => {
    const pan = panRef.current;
    if (!pan) return;
    touchedRef.current = true;
    setView((prev) => ({ ...prev, x: pan.vx + e.clientX - pan.px, y: pan.vy + e.clientY - pan.py }));
  };

  const onPointerUp = () => {
    panRef.current = null;
  };

  const onCreateGroup = (e: FormEvent) => {
    e.preventDefault();
    createGroup(project.id, name.trim());
    setName('');
  };

  const agentById = (id: number) => projectAgents.find((a) => a.id === id);

  const onDragOver = (e: DragEvent, key: string) => {
    if (!isAgentDrag(e)) return;
    e.preventDefault();
    setOverKey(key);
  };

  const onDropOnGroup = (e: DragEvent, groupId: number) => {
    e.preventDefault();
    setOverKey(null);
    const payload = readAgentDrag(e);
    const group = projectGroups.find((g) => g.id === groupId);
    if (!payload || !group) return;
    if (group.memberIds.includes(payload.agentId)) {
      onNotice(`${agentById(payload.agentId)?.name} 은(는) 이미 ${group.name} 의 멤버입니다.`);
      return;
    }
    addGroupMember(groupId, payload.agentId);
    // 다른 그룹의 노드를 끌어 온 경우는 "옮기기"이므로 원래 그룹에서는 뺀다.
    if (payload.fromGroupId !== undefined && payload.fromGroupId !== groupId) {
      removeGroupMember(payload.fromGroupId, payload.agentId);
    }
  };

  const onDeleteGroup = (id: number, groupName: string) => {
    if (!window.confirm(`"${groupName}" 그룹을 삭제할까요? 에이전트는 삭제되지 않습니다.`)) return;
    deleteGroup(id);
  };

  const isEmpty = projectAgents.length === 0 && projectGroups.length === 0;

  return (
    <section className={styles.canvas}>
      <div className={styles.toolbar}>
        <h2 className={styles.title}>구성도</h2>
        <form onSubmit={onCreateGroup} className={styles.groupForm}>
          <input placeholder="새 그룹 이름 (예: Backend Team)" value={name} onChange={(e) => setName(e.target.value)} required />
          <button type="submit">+ 그룹 추가</button>
        </form>
        <div className={styles.zoom}>
          <button type="button" onClick={() => zoomBy(1 / ZOOM_STEP)} aria-label="축소">
            −
          </button>
          <span className={styles.zoomValue}>{Math.round(view.scale * 100)}%</span>
          <button type="button" onClick={() => zoomBy(ZOOM_STEP)} aria-label="확대">
            +
          </button>
          <button
            type="button"
            onClick={() => {
              touchedRef.current = false;
              fit();
            }}
          >
            맞춤
          </button>
        </div>
      </div>

      <div
        ref={viewportRef}
        className={styles.viewport}
        style={vars({ '--vx': `${view.x}px`, '--vy': `${view.y}px`, '--vs': view.scale })}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
      >
        <div className={styles.world}>
          {layout.boxes.map((box) => {
            return (
              <div
                key={box.key}
                className={`${styles.box} ${overKey === box.key ? styles.boxOver : ''}`}
                style={vars({ '--x': `${box.x}px`, '--y': `${box.y}px`, '--w': `${box.w}px`, '--h': `${box.h}px` })}
                onDragOver={(e) => onDragOver(e, box.key)}
                onDragLeave={() => setOverKey(null)}
                onDrop={(e) => onDropOnGroup(e, box.groupId)}
              >
                <div className={styles.boxHeader}>
                  <div className={styles.boxTitles}>
                    <strong className={styles.boxTitle}>{box.title}</strong>
                    <span className={shared.muted}>{box.subtitle}</span>
                  </div>
                  <button
                    type="button"
                    className={styles.boxDelete}
                    onClick={() => onDeleteGroup(box.groupId, box.title)}
                    aria-label={`${box.title} 그룹 삭제`}
                  >
                    삭제
                  </button>
                </div>
                {box.isEmpty && <div className={styles.emptyDrop}>에이전트를 여기로 끌어 놓으세요</div>}
              </div>
            );
          })}

          <svg className={styles.edges} width={layout.width} height={layout.height} aria-hidden="true">
            {layout.edges.map((edge) => (
              <path key={edge.key} d={edge.d} className={styles.edge} />
            ))}
          </svg>

          {layout.nodes.map((node) => {
            const agent = agentById(node.agentId);
            if (!agent) return null;
            const role = SEED_ROLES.find((r) => r.id === agent.roleId);
            const reason = unavailableReason(agent, providers, project);
            const inGroup = node.groupId !== null;
            return (
              <div
                key={node.key}
                className={`${styles.node} ${node.isLeader ? styles.nodeLeader : ''} ${reason ? styles.nodeUnavailable : ''}`}
                style={vars({ '--x': `${node.x}px`, '--y': `${node.y}px` })}
                draggable
                onDragStart={(e) =>
                  startAgentDrag(e, node.groupId === null ? { agentId: agent.id } : { agentId: agent.id, fromGroupId: node.groupId })
                }
                title={reason ?? undefined}
              >
                {inGroup && (
                  <button
                    type="button"
                    className={`${styles.iconButton} ${node.isLeader ? styles.leader : ''}`}
                    title={node.isLeader ? '리더' : '리더로 지정'}
                    aria-label={node.isLeader ? `${agent.name} 리더` : `${agent.name} 리더로 지정`}
                    onClick={() => setGroupLeader(node.groupId!, agent.id)}
                  >
                    {node.isLeader ? '★' : '☆'}
                  </button>
                )}
                <div className={styles.nodeText}>
                  <span className={styles.nodeName}>{agent.name}</span>
                  <span className={styles.nodeMeta}>
                    {role?.name} · {agent.model || '기본 모델'}
                  </span>
                </div>
                {reason && <span className={styles.warnDot} aria-label={reason} />}
                {inGroup && (
                  <button
                    type="button"
                    className={styles.iconButton}
                    title="그룹에서 제거"
                    aria-label={`${agent.name} 그룹에서 제거`}
                    onClick={() => removeGroupMember(node.groupId!, agent.id)}
                  >
                    ×
                  </button>
                )}
              </div>
            );
          })}
        </div>

        {isEmpty && (
          <div className={styles.emptyState}>
            <strong>아직 에이전트와 그룹이 없습니다</strong>
            <span>왼쪽에서 새 에이전트를 만들고, 위에서 그룹을 추가해 보세요.</span>
          </div>
        )}
      </div>

      <div className={styles.legend}>
        <span>★ 리더</span>
        <span>노드를 그룹으로 끌어 놓기</span>
        <span>배경 드래그로 이동 · 휠로 확대/축소</span>
        <span>그룹에 속하지 않아도 됩니다</span>
      </div>
    </section>
  );
}
