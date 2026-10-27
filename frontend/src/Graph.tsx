import { useEffect, useRef, useMemo } from "react";
import cytoscape from "cytoscape";
import type { Row } from "./api";
export default function Graph({
  nodes,
  edges,
  dark,
  onSelect,
}: {
  nodes: Row[];
  edges: Row[];
  dark: boolean;
  onSelect: (row: Row) => void;
}) {
  const root = useRef<HTMLDivElement>(null),
    cy = useRef<cytoscape.Core | null>(null);
  const selectRef = useRef(onSelect);
  selectRef.current = onSelect;
  const styles = useMemo<cytoscape.StylesheetCSS[]>(
    () => [
      {
        selector: "node",
        css: {
          label: "data(label)",
          "background-color": dark ? "#243e5d" : "#edf4ff",
          "border-color": dark ? "#6d9deb" : "#719bd8",
          "border-width": 1.5,
          shape: "round-rectangle",
          width: 145,
          height: 48,
          color: dark ? "#e7edf8" : "#23374f",
          "font-size": 12,
          "text-valign": "center",
          "text-wrap": "wrap",
          "text-max-width": "135px",
        },
      },
      {
        selector: 'node[kind="Method"]',
        css: {
          "background-color": dark ? "#193a3b" : "#edf9f5",
          "border-color": "#369e8d",
        },
      },
      {
        selector: 'node[kind="BusinessRule"]',
        css: {
          "background-color": dark ? "#3c3222" : "#fff8eb",
          "border-color": "#c69a50",
          shape: "round-rectangle",
        },
      },
      {selector:'node[kind="BusinessModule"],node[kind="BusinessConcept"]',css:{"background-color":dark?"#40334e":"#f5eaff","border-color":"#a277ca",shape:"round-rectangle"}},
      {selector:'node[kind="ControlStructure"]',css:{"background-color":dark?"#3e3323":"#fff4e3","border-color":"#c58f3d",shape:"diamond"}},
      {
        selector: 'node[kind="Interface"]',
        css: {
          "background-color": dark ? "#332c4a" : "#f6f2ff",
          "border-color": "#a191c7",
        },
      },
      {
        selector: "edge",
        css: {
          label: "data(label)",
          "curve-style": "bezier",
          "target-arrow-shape": "triangle",
          "line-color": dark ? "#5d7495" : "#afc0d8",
          "target-arrow-color": dark ? "#5d7495" : "#afc0d8",
          width: 1.3,
          "font-size": 9,
          color: dark ? "#a8bbd6" : "#6a7d96",
          "text-background-color": dark ? "#131f30" : "#f8faff",
          "text-background-opacity": 1,
          "text-background-padding": "3px",
          "text-rotation": "autorotate",
        },
      },
      {
        selector: ":selected",
        css: {
          "border-color": "#285ee6",
          "border-width": 3,
          "line-color": "#285ee6",
          "target-arrow-color": "#285ee6",
        },
      },
    ],
    [dark],
  );
  useEffect(() => {
    const ids = new Set(nodes.map((n) => n.id));
    cy.current = cytoscape({
      container: root.current,
      elements: [
        ...nodes.map((n) => ({ data: { ...n, label: n.name } })),
        ...edges
          .filter((e) => ids.has(e.source) && ids.has(e.target))
          .map((e) => ({ data: { ...e, label: e.type } })),
      ],
      layout: {
        name: "cose",
        animate: false,
        padding: 35,
        componentSpacing: 80,
        nodeRepulsion: () => 8000,
        idealEdgeLength: () => 100,
        numIter: 700,
      },
      style: styles,
    });
    cy.current.on("tap", "node, edge", (e) =>
      selectRef.current(e.target.data()),
    );
    const observer = new ResizeObserver(() => cy.current?.resize());
    if (root.current) observer.observe(root.current);
    return () => {
      observer.disconnect();
      cy.current?.destroy();
    };
  }, [nodes, edges]);
  useEffect(() => {
    cy.current?.style(styles).update();
  }, [styles]);
  return (
    <div className="graph-wrap">
      <div className="graph-tools">
        <button onClick={() => cy.current?.fit(undefined, 40)}>⊡ Fit</button>
        <button onClick={() => cy.current?.zoom(cy.current.zoom() * 1.2)}>
          +
        </button>
        <button onClick={() => cy.current?.zoom(cy.current.zoom() / 1.2)}>
          −
        </button>
      </div>
      <div ref={root} className="graph" aria-label="Interactive graph" />
    </div>
  );
}
