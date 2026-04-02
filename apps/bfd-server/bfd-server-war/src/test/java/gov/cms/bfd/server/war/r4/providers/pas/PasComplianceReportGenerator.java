package gov.cms.bfd.server.war.r4.providers.pas;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates an HTML compliance report for CMS-0057-F Da Vinci PAS IG audit conformance. The report
 * visualizes all three layers of validation:
 *
 * <ul>
 *   <li><b>Layer 1</b>: Profile-Level Validation (FhirInstanceValidator + PAS IG NPM package)
 *   <li><b>Layer 2</b>: HL7 FHIR Validator CLI (independent verification)
 *   <li><b>Layer 3</b>: Explicit Assertion Tests (programmatic checks)
 * </ul>
 *
 * <p>Output: {@code target/pas-compliance-report.html}
 */
final class PasComplianceReportGenerator {

  private PasComplianceReportGenerator() {}

  /** A single check result within a validation layer. */
  static class CheckResult {
    final String name;
    final String description;
    final boolean passed;
    final String details;
    final String severity; // "error", "warning", "info"

    CheckResult(String name, String description, boolean passed, String details, String severity) {
      this.name = name;
      this.description = description;
      this.passed = passed;
      this.details = details;
      this.severity = severity;
    }

    static CheckResult pass(String name, String description) {
      return new CheckResult(name, description, true, null, "info");
    }

    static CheckResult pass(String name, String description, String details) {
      return new CheckResult(name, description, true, details, "info");
    }

    static CheckResult fail(String name, String description, String details) {
      return new CheckResult(name, description, false, details, "error");
    }

    static CheckResult warn(String name, String description, String details) {
      return new CheckResult(name, description, true, details, "warning");
    }
  }

  /** A group of checks for a specific resource or category within a layer. */
  static class CheckGroup {
    final String title;
    final String profileUrl;
    final List<CheckResult> checks = new ArrayList<>();

    CheckGroup(String title, String profileUrl) {
      this.title = title;
      this.profileUrl = profileUrl;
    }

    void add(CheckResult result) {
      checks.add(result);
    }

    boolean allPassed() {
      return checks.stream().allMatch(c -> c.passed);
    }

    long passCount() {
      return checks.stream().filter(c -> c.passed).count();
    }

    long failCount() {
      return checks.stream().filter(c -> !c.passed).count();
    }
  }

  /** A validation layer (1, 2, or 3). */
  static class Layer {
    final int number;
    final String name;
    final String description;
    final List<CheckGroup> groups = new ArrayList<>();

    Layer(int number, String name, String description) {
      this.number = number;
      this.name = name;
      this.description = description;
    }

    void addGroup(CheckGroup group) {
      groups.add(group);
    }

    boolean allPassed() {
      return groups.stream().allMatch(CheckGroup::allPassed);
    }

    long totalChecks() {
      return groups.stream().mapToLong(g -> g.checks.size()).sum();
    }

    long passedChecks() {
      return groups.stream().mapToLong(CheckGroup::passCount).sum();
    }

    long failedChecks() {
      return groups.stream().mapToLong(CheckGroup::failCount).sum();
    }
  }

  /**
   * Generates the HTML compliance report.
   *
   * @param outputPath path to write the HTML file
   * @param layers the validation layers to include
   * @param pasIgVersion the PAS IG version used
   * @param pasIgLoaded whether the PAS IG NPM package was successfully loaded
   * @throws IOException if writing fails
   */
  static void generate(String outputPath, List<Layer> layers, String pasIgVersion, boolean pasIgLoaded)
      throws IOException {
    String timestamp =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    boolean allPassed = layers.stream().allMatch(Layer::allPassed);
    long totalChecks = layers.stream().mapToLong(Layer::totalChecks).sum();
    long passedChecks = layers.stream().mapToLong(Layer::passedChecks).sum();
    long failedChecks = layers.stream().mapToLong(Layer::failedChecks).sum();

    try (PrintWriter w = new PrintWriter(new FileWriter(outputPath))) {
      w.println("<!DOCTYPE html>");
      w.println("<html lang=\"en\">");
      w.println("<head>");
      w.println("<meta charset=\"UTF-8\">");
      w.println(
          "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
      w.println("<title>CMS-0057-F PAS IG Compliance Report</title>");
      w.println("<style>");
      writeCSS(w);
      w.println("</style>");
      w.println("</head>");
      w.println("<body>");

      // Header
      w.println("<div class=\"header\">");
      w.println("  <div class=\"header-content\">");
      w.println("  <div>");
      w.println("    <h1>CMS-0057-F Compliance Report</h1>");
      w.println(
          "    <p class=\"subtitle\">Da Vinci Prior Authorization Support (PAS) IG Conformance Verification</p>");
      w.println("  </div>");
      w.println("  <div class=\"badge-container\">");
      if (allPassed) {
        w.println("    <div class=\"badge badge-pass\">COMPLIANT</div>");
      } else {
        w.println("    <div class=\"badge badge-fail\">NON-COMPLIANT</div>");
      }
      w.println("  </div>");
      w.println("  </div>");
      w.println("</div>");

      // Main content
      w.println("<div class=\"container\">");

      // Meta info
      w.println("<div class=\"meta-bar\">");
      w.println("  <div class=\"meta-item\">");
      w.println("    <span class=\"meta-label\">Report Generated</span>");
      w.println("    <span class=\"meta-value\">" + timestamp + " UTC</span>");
      w.println("  </div>");
      w.println("  <div class=\"meta-item\">");
      w.println("    <span class=\"meta-label\">PAS IG Version</span>");
      w.println("    <span class=\"meta-value\">" + pasIgVersion + "</span>");
      w.println("  </div>");
      w.println("  <div class=\"meta-item\">");
      w.println("    <span class=\"meta-label\">PAS IG Package</span>");
      w.println(
          "    <span class=\"meta-value "
              + (pasIgLoaded ? "text-pass" : "text-fail")
              + "\">"
              + (pasIgLoaded ? "Loaded" : "NOT Loaded")
              + "</span>");
      w.println("  </div>");
      w.println("  <div class=\"meta-item\">");
      w.println("    <span class=\"meta-label\">Validation Engine</span>");
      w.println("    <span class=\"meta-value\">HAPI FHIR FhirInstanceValidator</span>");
      w.println("  </div>");
      w.println("</div>");

      // Summary cards
      w.println("<div class=\"summary-grid\">");
      w.println("  <div class=\"summary-card\">");
      w.println("    <div class=\"summary-number\">" + totalChecks + "</div>");
      w.println("    <div class=\"summary-label\">Total Checks</div>");
      w.println("  </div>");
      w.println("  <div class=\"summary-card card-pass\">");
      w.println("    <div class=\"summary-number\">" + passedChecks + "</div>");
      w.println("    <div class=\"summary-label\">Passed</div>");
      w.println("  </div>");
      w.println("  <div class=\"summary-card " + (failedChecks > 0 ? "card-fail" : "card-pass") + "\">");
      w.println("    <div class=\"summary-number\">" + failedChecks + "</div>");
      w.println("    <div class=\"summary-label\">Failed</div>");
      w.println("  </div>");
      w.println("  <div class=\"summary-card\">");
      w.println("    <div class=\"summary-number\">" + layers.size() + "</div>");
      w.println("    <div class=\"summary-label\">Validation Layers</div>");
      w.println("  </div>");
      w.println("</div>");

      // Layers
      for (Layer layer : layers) {
        writeLayer(w, layer);
      }

      // Footer
      w.println("<div class=\"footer\">");
      w.println(
          "  <p>This report was auto-generated by the BFD PAS Integration test suite. "
              + "It validates resources against Da Vinci PAS IG StructureDefinitions "
              + "using HAPI FHIR's FhirInstanceValidator with the PAS IG NPM package.</p>");
      w.println(
          "  <p>For CMS-0057-F audit purposes, this report demonstrates programmatic "
              + "conformance verification of all PAS-profiled FHIR resources.</p>");
      w.println("</div>");

      w.println("</div>"); // container
      w.println("<script>");
      writeJS(w);
      w.println("</script>");
      w.println("</body>");
      w.println("</html>");
    }
  }

  private static void writeLayer(PrintWriter w, Layer layer) {
    String statusClass = layer.allPassed() ? "layer-pass" : "layer-fail";
    String statusIcon = layer.allPassed() ? "&#10003;" : "&#10007;";
    String statusText = layer.allPassed() ? "PASS" : "FAIL";

    w.println("<div class=\"layer " + statusClass + "\">");
    w.println("  <div class=\"layer-header\" onclick=\"toggleLayer(this)\">");
    w.println("    <div class=\"layer-title\">");
    w.println(
        "      <span class=\"layer-number\">Layer " + layer.number + "</span>");
    w.println("      <span class=\"layer-name\">" + layer.name + "</span>");
    w.println(
        "      <span class=\"layer-status "
            + (layer.allPassed() ? "status-pass" : "status-fail")
            + "\">"
            + statusIcon
            + " "
            + statusText
            + "</span>");
    w.println("    </div>");
    w.println(
        "    <div class=\"layer-desc\">"
            + layer.description
            + "</div>");
    w.println(
        "    <div class=\"layer-summary\">"
            + layer.passedChecks()
            + "/"
            + layer.totalChecks()
            + " checks passed</div>");
    w.println("  </div>");
    w.println("  <div class=\"layer-body\">");

    for (CheckGroup group : layer.groups) {
      writeCheckGroup(w, group);
    }

    w.println("  </div>");
    w.println("</div>");
  }

  private static void writeCheckGroup(PrintWriter w, CheckGroup group) {
    String statusClass = group.allPassed() ? "group-pass" : "group-fail";

    w.println("    <div class=\"check-group " + statusClass + "\">");
    w.println("      <div class=\"group-header\">");
    w.println("        <span class=\"group-title\">" + escapeHtml(group.title) + "</span>");
    if (group.profileUrl != null) {
      w.println(
          "        <span class=\"group-profile\"><code>"
              + escapeHtml(group.profileUrl)
              + "</code></span>");
    }
    w.println(
        "        <span class=\"group-count\">"
            + group.passCount()
            + "/"
            + group.checks.size()
            + "</span>");
    w.println("      </div>");
    w.println("      <div class=\"checks-list\">");

    for (CheckResult check : group.checks) {
      writeCheck(w, check);
    }

    w.println("      </div>");
    w.println("    </div>");
  }

  private static void writeCheck(PrintWriter w, CheckResult check) {
    String icon = check.passed ? "&#10003;" : "&#10007;";
    String rowClass = check.passed ? "check-pass" : "check-fail";

    w.println("        <div class=\"check-row " + rowClass + "\">");
    w.println("          <span class=\"check-icon\">" + icon + "</span>");
    w.println("          <div class=\"check-content\">");
    w.println(
        "            <span class=\"check-name\">" + escapeHtml(check.name) + "</span>");
    w.println(
        "            <span class=\"check-desc\">"
            + escapeHtml(check.description)
            + "</span>");
    if (check.details != null && !check.details.isEmpty()) {
      w.println(
          "            <div class=\"check-details "
              + "detail-"
              + check.severity
              + "\">"
              + escapeHtml(check.details)
              + "</div>");
    }
    w.println("          </div>");
    w.println("        </div>");
  }

  private static void writeCSS(PrintWriter w) {
    w.println(
        ":root {"
            + "  --pass: #16a34a; --pass-bg: #f0fdf4; --pass-border: #bbf7d0;"
            + "  --fail: #dc2626; --fail-bg: #fef2f2; --fail-border: #fecaca;"
            + "  --warn: #d97706; --warn-bg: #fffbeb; --warn-border: #fde68a;"
            + "  --blue: #2563eb; --blue-bg: #eff6ff; --blue-border: #bfdbfe;"
            + "  --gray-50: #f9fafb; --gray-100: #f3f4f6; --gray-200: #e5e7eb;"
            + "  --gray-300: #d1d5db; --gray-400: #9ca3af; --gray-500: #6b7280;"
            + "  --gray-600: #4b5563; --gray-700: #374151; --gray-800: #1f2937;"
            + "  --gray-900: #111827;"
            + "}");
    w.println(
        "* { margin: 0; padding: 0; box-sizing: border-box; }"
            + "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, "
            + "'Helvetica Neue', Arial, sans-serif; background: var(--gray-50); "
            + "color: var(--gray-800); line-height: 1.6; }");
    w.println(
        ".header { background: linear-gradient(135deg, #1e3a5f 0%, #0f172a 100%); "
            + "color: white; padding: 2rem 0; }"
            + ".header-content { max-width: 1200px; margin: 0 auto; padding: 0 2rem; "
            + "display: flex; justify-content: space-between; align-items: center; }"
            + ".header h1 { font-size: 1.75rem; font-weight: 700; letter-spacing: -0.025em; }"
            + ".subtitle { color: #94a3b8; margin-top: 0.25rem; font-size: 0.95rem; }");
    w.println(
        ".badge-container { text-align: right; }"
            + ".badge { display: inline-block; padding: 0.5rem 1.5rem; border-radius: 999px; "
            + "font-weight: 700; font-size: 1.1rem; letter-spacing: 0.1em; }"
            + ".badge-pass { background: var(--pass); color: white; box-shadow: 0 0 20px rgba(22,163,106,0.4); }"
            + ".badge-fail { background: var(--fail); color: white; box-shadow: 0 0 20px rgba(220,38,38,0.4); }");
    w.println(
        ".container { max-width: 1200px; margin: 0 auto; padding: 2rem; }");
    w.println(
        ".meta-bar { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); "
            + "gap: 1rem; margin-bottom: 2rem; }"
            + ".meta-item { background: white; border: 1px solid var(--gray-200); "
            + "border-radius: 0.5rem; padding: 1rem; }"
            + ".meta-label { display: block; font-size: 0.75rem; text-transform: uppercase; "
            + "letter-spacing: 0.05em; color: var(--gray-500); margin-bottom: 0.25rem; }"
            + ".meta-value { font-weight: 600; font-size: 0.9rem; }"
            + ".text-pass { color: var(--pass); } .text-fail { color: var(--fail); }");
    w.println(
        ".summary-grid { display: grid; grid-template-columns: repeat(4, 1fr); "
            + "gap: 1rem; margin-bottom: 2rem; }"
            + ".summary-card { background: white; border: 1px solid var(--gray-200); "
            + "border-radius: 0.75rem; padding: 1.5rem; text-align: center; }"
            + ".summary-number { font-size: 2.5rem; font-weight: 800; color: var(--gray-800); }"
            + ".summary-label { font-size: 0.85rem; color: var(--gray-500); margin-top: 0.25rem; }"
            + ".card-pass .summary-number { color: var(--pass); }"
            + ".card-fail .summary-number { color: var(--fail); }");
    w.println(
        ".layer { background: white; border: 1px solid var(--gray-200); "
            + "border-radius: 0.75rem; margin-bottom: 1.5rem; overflow: hidden; }"
            + ".layer-pass { border-left: 4px solid var(--pass); }"
            + ".layer-fail { border-left: 4px solid var(--fail); }"
            + ".layer-header { padding: 1.25rem 1.5rem; cursor: pointer; "
            + "user-select: none; transition: background 0.15s; }"
            + ".layer-header:hover { background: var(--gray-50); }"
            + ".layer-title { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; }"
            + ".layer-number { background: var(--blue); color: white; padding: 0.2rem 0.7rem; "
            + "border-radius: 999px; font-size: 0.8rem; font-weight: 600; }"
            + ".layer-name { font-size: 1.15rem; font-weight: 600; }"
            + ".layer-status { margin-left: auto; font-weight: 700; font-size: 0.85rem; "
            + "padding: 0.2rem 0.8rem; border-radius: 999px; }"
            + ".status-pass { background: var(--pass-bg); color: var(--pass); border: 1px solid var(--pass-border); }"
            + ".status-fail { background: var(--fail-bg); color: var(--fail); border: 1px solid var(--fail-border); }"
            + ".layer-desc { color: var(--gray-500); font-size: 0.85rem; margin-top: 0.5rem; }"
            + ".layer-summary { color: var(--gray-400); font-size: 0.8rem; margin-top: 0.25rem; }");
    w.println(
        ".layer-body { padding: 0 1.5rem 1.5rem; }"
            + ".check-group { border: 1px solid var(--gray-200); border-radius: 0.5rem; "
            + "margin-bottom: 1rem; overflow: hidden; }"
            + ".group-pass { } .group-fail { border-color: var(--fail-border); }"
            + ".group-header { display: flex; align-items: center; gap: 0.75rem; "
            + "padding: 0.75rem 1rem; background: var(--gray-50); "
            + "border-bottom: 1px solid var(--gray-200); flex-wrap: wrap; }"
            + ".group-title { font-weight: 600; font-size: 0.95rem; }"
            + ".group-profile { color: var(--gray-400); font-size: 0.75rem; }"
            + ".group-profile code { background: var(--gray-100); padding: 0.1rem 0.4rem; "
            + "border-radius: 0.25rem; font-size: 0.7rem; }"
            + ".group-count { margin-left: auto; font-size: 0.8rem; color: var(--gray-400); "
            + "font-weight: 600; }");
    w.println(
        ".checks-list { padding: 0.25rem 0; }"
            + ".check-row { display: flex; align-items: flex-start; gap: 0.75rem; "
            + "padding: 0.6rem 1rem; border-bottom: 1px solid var(--gray-100); }"
            + ".check-row:last-child { border-bottom: none; }"
            + ".check-pass .check-icon { color: var(--pass); font-weight: 700; min-width: 1.2rem; }"
            + ".check-fail .check-icon { color: var(--fail); font-weight: 700; min-width: 1.2rem; }"
            + ".check-content { flex: 1; }"
            + ".check-name { font-weight: 600; font-size: 0.88rem; display: block; }"
            + ".check-desc { font-size: 0.8rem; color: var(--gray-500); display: block; }"
            + ".check-details { font-size: 0.75rem; margin-top: 0.35rem; padding: 0.5rem; "
            + "border-radius: 0.25rem; font-family: 'SF Mono', 'Fira Code', monospace; "
            + "white-space: pre-wrap; word-break: break-all; }"
            + ".detail-info { background: var(--blue-bg); color: var(--blue); border: 1px solid var(--blue-border); }"
            + ".detail-error { background: var(--fail-bg); color: var(--fail); border: 1px solid var(--fail-border); }"
            + ".detail-warning { background: var(--warn-bg); color: var(--warn); border: 1px solid var(--warn-border); }");
    w.println(
        ".footer { margin-top: 2rem; padding: 1.5rem; background: var(--gray-100); "
            + "border-radius: 0.5rem; font-size: 0.8rem; color: var(--gray-500); "
            + "line-height: 1.7; }"
            + ".footer p + p { margin-top: 0.5rem; }");
    w.println(
        "@media (max-width: 768px) {"
            + "  .summary-grid { grid-template-columns: repeat(2, 1fr); }"
            + "  .header-content { flex-direction: column; gap: 1rem; text-align: center; }"
            + "  .badge-container { text-align: center; }"
            + "  .layer-title { flex-direction: column; align-items: flex-start; }"
            + "  .layer-status { margin-left: 0; }"
            + "}");
  }

  private static void writeJS(PrintWriter w) {
    w.println(
        "function toggleLayer(header) {"
            + "  var body = header.nextElementSibling;"
            + "  if (body.style.display === 'none') {"
            + "    body.style.display = 'block';"
            + "  } else {"
            + "    body.style.display = 'none';"
            + "  }"
            + "}");
  }

  private static String escapeHtml(String text) {
    if (text == null) {
      return "";
    }
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
