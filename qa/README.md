# Medinex QA Automation & Comprehensive Test Framework

This repository directory contains the automated end-to-end (E2E), API, security, performance, and reporting framework for **Medinex** (Intelligent Clinical Medicine Management System).

The test suite structure follows the reference repository architecture with **1,800 total automated test cases** (300 cases per category across 6 categories), full Excel reports, interactive HTML reports, and continuous integration via GitHub Actions.

---

## 📁 Repository Directory Structure

```text
qa/
├── selenium_web/             # Category A: Web Selenium E2E (300 cases)
│   ├── package.json          # Node.js WebDriver runner configuration
│   ├── test_runner.js        # Selenium headless browser runner
│   ├── test_web_runner.py    # Python Selenium & DOM test runner
│   └── test_cases.json       # 300 Web E2E test case specifications & results
├── appium_mobile/            # Category B: Android Appium E2E (300 cases)
│   ├── package.json          # WebdriverIO & UiAutomator2 configuration
│   ├── wdio.conf.js          # Appium configuration pointing to app-debug.apk
│   ├── test_mobile_runner.py # Mobile test validator & APK inspector
│   └── test_cases.json       # 300 Android Mobile test case specifications & results
├── backend_service/          # Category C: Backend/API Service Tests (300 cases)
│   ├── test_api_service.py   # FastAPI TestClient & contract verification runner
│   └── test_cases.json       # 300 Backend API test case specifications & results
├── security_scan/            # Category D: Backend Security Tests (300 cases)
│   ├── test_backend_security.py # OWASP API security & vulnerability scanner
│   └── test_cases.json       # 300 Backend Security test case specifications & results
├── security_e2e/             # Category E: Security E2E Tests (300 cases)
│   ├── test_security_e2e.py  # Client-side XSS, manifest flags & HTTPS audit
│   └── test_cases.json       # 300 Security E2E test case specifications & results
├── load_testing/             # Category F: Performance and Load Tests (300 cases)
│   ├── locustfile.py         # Locust performance test definition
│   ├── load_test_runner.py   # 100 VU / 60s baseline load benchmark runner
│   ├── load_metrics.json     # Measured latency and throughput metrics
│   └── test_cases.json       # 300 Performance & Load test specifications & results
├── test_data/                # Authorized, lightweight test fixtures
│   ├── valid_label.jpg       # Clinical medicine carton label
│   ├── augmentin_label.jpg   # Multi-strength combination packaging
│   ├── corrupt_image.jpg     # Corrupted header test file
│   ├── empty.jpg             # 0-byte boundary file
│   ├── non_medicine.jpg      # Negative control image (natural plant)
│   ├── tiny_1x1.png          # 1x1 boundary test image
│   ├── script_payload.sh     # Security test payload
│   └── unsupported_file.txt  # Unsupported MIME type file
├── reports/                  # Generated Test Reports
│   ├── E2E_Test_Report_Medinex.xlsx # 9-worksheet executive Excel report
│   ├── unified_executive_report.html # Unified executive HTML dashboard
│   ├── web_selenium_report.html     # Web Selenium category report
│   ├── android_appium_report.html   # Android Appium category report
│   ├── backend_service_report.html  # Backend Service category report
│   ├── backend_security_report.html # Backend Security category report
│   ├── security_e2e_report.html     # Security E2E category report
│   ├── performance_load_report.html # Performance Load category report
│   └── github_step_summary.md       # Formatted GitHub Actions Step Summary
├── build_qa_suite.py         # Test case catalog generator (1,800 cases)
├── run_all_tests.py          # Master test orchestrator
└── README.md                 # Complete setup and execution documentation
```

---

## ⚡ Quick Start: Windows PowerShell Commands

Run all commands from the repository root: `C:\Users\Lenovo\AndroidStudioProjects\Medinex`.

### 1. Install QA Prerequisites

```powershell
pip install openpyxl requests pytest httpx fastapi pillow numpy python-multipart
```

### 2. Run the Full QA Test Suite (All 6 Categories + Excel/HTML Reports)

```powershell
python qa/run_all_tests.py
```

---

## 🧪 Individual Suite Execution Commands

### Category A: Web Selenium E2E Tests (300 Tests)
```powershell
# Python DOM & Web runner
python qa/selenium_web/test_web_runner.py

# Or Node.js Selenium runner (if Node is installed)
node qa/selenium_web/test_runner.js
```

### Category B: Android Appium / Native Mobile Tests (300 Tests)
```powershell
# 1. Compile the Android Debug APK (if not already compiled)
.\gradlew.bat assembleDebug

# 2. Run Android Mobile Validator & APK Inspector
python qa/appium_mobile/test_mobile_runner.py

# 3. (Optional) Run WebdriverIO with Appium if an emulator is connected
npx wdio run qa/appium_mobile/wdio.conf.js
```

### Category C: Backend / API Service Tests (300 Tests)
```powershell
python qa/backend_service/test_api_service.py
```

### Category D: Backend Security Tests (300 Tests)
```powershell
python qa/security_scan/test_backend_security.py
```

### Category E: Security E2E Client Tests (300 Tests)
```powershell
python qa/security_e2e/test_security_e2e.py
```

### Category F: Performance and Load Tests (300 Tests)
```powershell
# Run 100 VU baseline benchmark
python qa/load_testing/load_test_runner.py

# Or run interactive Locust web UI
locust -f qa/load_testing/locustfile.py --host=http://127.0.0.1:8000
```

---

## 📊 Generating Reports

### Generate Excel Report (`E2E_Test_Report_Medinex.xlsx`)
```powershell
python qa/reports/generate_excel_report.py
```
Output: `qa/reports/E2E_Test_Report_Medinex.xlsx`
Worksheets included:
1. **Executive Summary** (KPI cards, pass rate, category breakdown, load metrics)
2. **Web Selenium E2E** (300 cases)
3. **Android Appium E2E** (300 cases)
4. **Backend Service Tests** (300 cases)
5. **Backend Security Tests** (300 cases)
6. **Security E2E Tests** (300 cases)
7. **Performance and Load Tests** (300 cases)
8. **Failed and Blocked Tests**
9. **Screen Coverage** (24 application screens and modules)

### Generate HTML Reports
```powershell
python qa/reports/generate_html_reports.py
```
Outputs:
- `qa/reports/unified_executive_report.html`
- `qa/reports/web_selenium_report.html`
- `qa/reports/android_appium_report.html`
- `qa/reports/backend_service_report.html`
- `qa/reports/backend_security_report.html`
- `qa/reports/security_e2e_report.html`
- `qa/reports/performance_load_report.html`

### Generate GitHub Actions Summary
```powershell
python qa/reports/generate_github_summary.py
```

---

## 🚀 GitHub Actions Continuous Integration

The dedicated CI pipeline is configured at:
`.github/workflows/medinex-qa.yml`

### Pipeline Jobs & Workflow Graph:
- **Build Android APK**: Compiles native Java debug APK using Gradle on JDK 17 and uploads `medinex-debug-apk`.
- **Android Appium E2E**: Depends on APK build, runs mobile test validator.
- **Web E2E Tests**: Executes Web E2E validation against the live website.
- **Security Review**: Runs OWASP backend security scan and security E2E audit.
- **Verify Live Web Deployment**: Verifies live endpoint contracts and executes 100 VU baseline load benchmark.
- **Unified Summary & Report Deployment**: Aggregates all results, builds `E2E_Test_Report_Medinex.xlsx`, HTML reports, and posts the detailed test cases summary table directly to `$GITHUB_STEP_SUMMARY`.

> **Note:** The existing GitHub Pages deployment workflow `.github/workflows/static.yml` remains completely untouched and operational.
