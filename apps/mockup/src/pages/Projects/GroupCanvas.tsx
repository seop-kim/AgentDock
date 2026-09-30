import {
  CSSProperties,
  DragEvent,
  PointerEvent,
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { unavailableReason } from '../../lib/agentAvailability';
import { layoutCanvas, NODE_H, NODE_W, type CanvasNode } from '../../lib/canvasLayout';
import { isAgentDrag, startAgentDrag } from '../../lib/dnd';
import { useGroupDrop } from '../../lib/useGroupDrop';
import { useMockStore } from '../../store/MockStore';
import { SEED_ROLES } from '../../store/seed';
import shared from '../../styles/shared.module.css';
import type { Agent, Project } from '../../types';
import AgentNodeMenu from './AgentNodeMenu';
import styles from './GroupCanvas.module.css';

interface View {
  x: number;
  y: number;
  scale: number;
}

const MIN_SCALE = 0.3;
const MAX_SCALE = 2;
const FIT_MARGIN = 24;
// 화면 위에 떠 있는 요소(헤더 카드, 채팅 창, 확대/축소 도구)가 가리는 영역. 맞춤은 이 바깥의 빈 곳에 맞춘다.
const INSET_TOP = 72;
const INSET_RIGHT = 24;
const ZOOM_STEP = 1.2;
const FOCUS_MIN_SCALE = 0.85;
const SMOOTH_MS = 450;

const clampScale = (scale: number) => Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale));

/** CSS 변수로 위치/크기를 넘긴다. 모양은 전부 GroupCanvas.module.css 가 정한다. */
const vars = (values: Record<string, string | number>) => values as unknown as CSSProperties;

/**
 * 프로젝트의 에이전트와 그룹을 보여 주는 캔버스. 그룹은 상자, 에이전트는 노드로 그리고 리더에서 멤버로 선을 잇는다.
 * 그룹에 속하지 않은 에이전트는 상자 없이 노드만 놓인다. 배경을 끌어 이동하고 휠로 확대/축소한다.
 * 에이전트 노드를 그룹 상자로 끌어 놓으면 멤버가 되고, 왼쪽 목록으로 끌면 그룹에서 빠진다.
 */
export default function GroupCanvas({
  project,
  insetLeft,
  insetBottom,
  selectedAgentId,
  focusSeq,
  onSelectAgent,
  onNotice,
}: {
  project: Project;
  /** 선택된 에이전트. 구성도의 해당 노드가 강조된다 */
  selectedAgentId: number | null;
  /** 값이 바뀔 때마다 선택된 에이전트 쪽으로 화면을 옮긴다(같은 에이전트를 다시 눌러도 이동) */
  focusSeq: number;
  onSelectAgent: (agentId: number) => void;
  /** 왼쪽에 떠 있는 에이전트/그룹 패널이 가리는 너비(px) */
  insetLeft: number;
  /** 아래에 떠 있는 채팅 창이 가리는 높이(px) */
  insetBottom: number;
  onNotice: (message: string) => void;
}) {
  const { agents, groups, providers, deleteGroup, removeGroupMember, setGroupLeader, setAgentPlaced } = useMockStore();
  const dropOnGroup = useGroupDrop(onNotice);
  const projectAgents = useMemo(() => agents.filter((a) => a.projectId === project.id), [agents, project.id]);
  // 구성도에는 "놓인" 에이전트만 그린다. 빼 둔 에이전트는 에이전트 목록에만 있다.
  const placedAgents = useMemo(() => projectAgents.filter((a) => a.placed), [projectAgents]);
  const projectGroups = useMemo(() => groups.filter((g) => g.projectId === project.id), [groups, project.id]);
  const layout = useMemo(() => layoutCanvas(placedAgents, projectGroups), [placedAgents, projectGroups]);

  const [view, setView] = useState<View>({ x: 0, y: 0, scale: 1 });
  const [overKey, setOverKey] = useState<string | null>(null);
  // 노드 위에 버튼을 늘어놓지 않고 "⋮" 메뉴 하나로 모은다.
  const [nodeMenu, setNodeMenu] = useState<{ node: CanvasNode; agent: Agent; rect: DOMRect } | null>(null);
  // 선택한 에이전트로 옮길 때만 부드럽게 움직이고, 드래그/휠은 즉시 따라간다.
  const [smooth, setSmooth] = useState(false);
  const smoothTimer = useRef<number | undefined>(undefined);
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
    const availW = width - insetLeft - INSET_RIGHT;
    const availH = height - INSET_TOP - insetBottom;
    const scale = Math.min(1, (availW - FIT_MARGIN * 2) / layout.width, (availH - FIT_MARGIN * 2) / layout.height);
    setView({
      scale,
      x: insetLeft + (availW - layout.width * scale) / 2,
      y: INSET_TOP + (availH - layout.height * scale) / 2,
    });
  }, [layout.width, layout.height, insetLeft, insetBottom]);

  useLayoutEffect(() => {
    if (!touchedRef.current) fit();
  }, [fit]);

  // 카드를 눌렀을 때: 선택한 에이전트의 노드가 보이는 영역(패널/채팅을 뺀 곳) 가운데 오도록 옮긴다.
  useEffect(() => {
    if (focusSeq === 0 || selectedAgentId === null) return;
    const node =
      layout.nodes.find((n) => n.agentId === selectedAgentId && n.groupId === null) ??
      layout.nodes.find((n) => n.agentId === selectedAgentId);
    const el = viewportRef.current;
    if (!node || !el) return;
    const { width, height } = el.getBoundingClientRect();
    const centerX = insetLeft + (width - insetLeft - INSET_RIGHT) / 2;
    const centerY = INSET_TOP + (height - INSET_TOP - insetBottom) / 2;
    touchedRef.current = true;
    setSmooth(true);
    window.clearTimeout(smoothTimer.current);
    smoothTimer.current = window.setTimeout(() => setSmooth(false), SMOOTH_MS);
    setView((prev) => {
      // 너무 멀리 축소된 상태면 식별할 수 있는 크기까지만 확대한다
      const scale = clampScale(Math.max(prev.scale, FOCUS_MIN_SCALE));
      return {
        scale,
        x: centerX - (node.x + NODE_W / 2) * scale,
        y: centerY - (node.y + NODE_H / 2) * scale,
      };
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusSeq]);

  useEffect(() => () => window.clearTimeout(smoothTimer.current), []);

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

  const agentById = (id: number) => projectAgents.find((a) => a.id === id);

  const onDragOver = (e: DragEvent, key: string) => {
    if (!isAgentDrag(e)) return;
    e.preventDefault();
    setOverKey(key);
  };

  const onDropOnGroup = (e: DragEvent, groupId: number) => {
    setOverKey(null);
    dropOnGroup(e, groupId);
  };

  const onDeleteGroup = (id: number, groupName: string) => {
    if (!window.confirm(`"${groupName}" 그룹을 삭제할까요? 에이전트는 삭제되지 않습니다.`)) return;
    deleteGroup(id);
  };

  const isEmpty = placedAgents.length === 0 && projectGroups.length === 0;

  return (
    <section className={styles.canvas}>
      <div
        ref={viewportRef}
        className={styles.viewport}
        style={vars({ '--vx': `${view.x}px`, '--vy': `${view.y}px`, '--vs': view.scale })}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
      >
        <div className={`${styles.world} ${smooth ? styles.worldSmooth : ''}`}>
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
            const isSelected = node.agentId === selectedAgentId;
            return (
              <div
                key={node.key}
                className={`${styles.node} ${node.isLeader ? styles.nodeLeader : ''} ${agent.id === project.masterAgentId ? styles.nodeMaster : ''} ${reason ? styles.nodeUnavailable : ''} ${isSelected ? styles.nodeSelected : ''}`}
                aria-current={isSelected ? 'true' : undefined}
                onClick={() => onSelectAgent(node.agentId)}
                style={vars({ '--x': `${node.x}px`, '--y': `${node.y}px` })}
                draggable
                onDragStart={(e) =>
                  startAgentDrag(e, node.groupId === null ? { agentId: agent.id } : { agentId: agent.id, fromGroupId: node.groupId })
                }
                title={reason ?? undefined}
              >
                {node.isLeader && (
                  <span className={styles.leaderMark} title="리더">
                    ★
                  </span>
                )}
                <div className={styles.nodeText}>
                  <span className={styles.nodeName}>{agent.name}</span>
                  <span className={styles.nodeMeta}>
                    {role?.name} · {agent.model || '기본 모델'}
                  </span>
                </div>
                {agent.id === project.masterAgentId && <span className={styles.masterTag}>마스터</span>}
                {reason && <span className={styles.warnDot} aria-label={reason} />}
                {isSelected && <span className={styles.selectedTag}>선택됨</span>}
                {/* 마스터는 그룹에 속하지 않고 구성도에서 뺄 수도 없어 노드에 할 동작이 없다 */}
                {agent.id !== project.masterAgentId && (
                  <button
                    type="button"
                    className={styles.nodeMenuButton}
                    aria-label={`${agent.name} 메뉴`}
                    aria-haspopup="menu"
                    onClick={(e) => {
                      e.stopPropagation();
                      setNodeMenu({ node, agent, rect: e.currentTarget.getBoundingClientRect() });
                    }}
                  >
                    ⋮
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

      {/* 오른쪽 위에 떠 있는 확대/축소 도구 */}
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

      {nodeMenu && (
        <AgentNodeMenu
          anchor={nodeMenu.rect}
          inGroup={nodeMenu.node.groupId !== null}
          isLeader={nodeMenu.node.isLeader}
          onClose={() => setNodeMenu(null)}
          onSetLeader={() => {
            const { node } = nodeMenu;
            setNodeMenu(null);
            if (node.groupId !== null) setGroupLeader(node.groupId, node.agentId);
          }}
          onRemoveFromGroup={() => {
            const { node } = nodeMenu;
            setNodeMenu(null);
            if (node.groupId !== null) removeGroupMember(node.groupId, node.agentId);
          }}
          onRemoveFromCanvas={() => {
            const agentId = nodeMenu.agent.id;
            setNodeMenu(null);
            setAgentPlaced(agentId, false);
          }}
        />
      )}
    </section>
  );
}
