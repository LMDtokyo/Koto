import { Route, Routes } from "react-router";
import { SiteLayout } from "@/components/SiteLayout";
import { HomePage } from "@/pages/HomePage";
import { DownloadPage } from "@/pages/DownloadPage";
import { DocsPage } from "@/pages/DocsPage";
import { UpdatesPage } from "@/pages/UpdatesPage";
import { NotFoundPage } from "@/pages/NotFoundPage";

export default function App() {
  return (
    <Routes>
      <Route element={<SiteLayout />}>
        <Route index element={<HomePage />} />
        <Route path="download" element={<DownloadPage />} />
        <Route path="docs" element={<DocsPage />} />
        <Route path="updates" element={<UpdatesPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
