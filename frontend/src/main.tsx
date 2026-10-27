import React, { Suspense, lazy } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { portalPage, useHash } from "./portal/router";
import "./style.css";

const PortalApp = lazy(() => import("./portal/PortalApp"));

function Root() {
  const hash = useHash();
  const page = portalPage(hash);
  if (page) {
    return (
      <Suspense fallback={null}>
        <PortalApp page={page} />
      </Suspense>
    );
  }
  return <App />;
}

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <Root />
  </React.StrictMode>,
);
