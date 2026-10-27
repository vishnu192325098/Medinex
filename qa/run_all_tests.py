"""
run_all_tests.py - Master Test Orchestrator for Medinex QA Automation
Executes all QA categories, runs local benchmarks, and generates Excel & HTML reports.
"""

import os
import sys
import time

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

QA_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(QA_DIR, ".."))

def run_all():
    print("=================================================================")
    print("[*] MEDINEX QA AUTOMATION -- MASTER TEST ORCHESTRATOR")
    print("=================================================================")
    t_start = time.time()

    # 1. Build / refresh test cases
    print("\n--- STEP 1: CATALOGING TEST CASES ---")
    os.system(f'python "{os.path.join(QA_DIR, "build_qa_suite.py")}"')

    # 2. Run Web Tests
    print("\n--- STEP 2: RUNNING WEB SELENIUM E2E TESTS ---")
    os.system(f'python "{os.path.join(QA_DIR, "selenium_web", "test_web_runner.py")}"')

    # 3. Run Android Mobile Tests
    print("\n--- STEP 3: RUNNING ANDROID APPIUM / MOBILE TESTS ---")
    os.system(f'python "{os.path.join(QA_DIR, "appium_mobile", "test_mobile_runner.py")}"')

    # 4. Run Backend Service Tests
    print("\n--- STEP 4: RUNNING BACKEND SERVICE TESTS ---")
    os.system(f'python "{os.path.join(QA_DIR, "backend_service", "test_api_service.py")}"')

    # 5. Run Backend Security Scan
    print("\n--- STEP 5: RUNNING BACKEND SECURITY SCAN ---")
    os.system(f'python "{os.path.join(QA_DIR, "security_scan", "test_backend_security.py")}"')

    # 6. Run Security E2E Tests
    print("\n--- STEP 6: RUNNING SECURITY E2E TESTS ---")
    os.system(f'python "{os.path.join(QA_DIR, "security_e2e", "test_security_e2e.py")}"')

    # 7. Run Performance Load Test
    print("\n--- STEP 7: RUNNING PERFORMANCE & LOAD TEST ---")
    os.system(f'python "{os.path.join(QA_DIR, "load_testing", "load_test_runner.py")}"')

    # 8. Generate Reports
    print("\n--- STEP 8: GENERATING EXCEL AND HTML REPORTS ---")
    os.system(f'python "{os.path.join(QA_DIR, "reports", "generate_excel_report.py")}"')
    os.system(f'python "{os.path.join(QA_DIR, "reports", "generate_html_reports.py")}"')
    os.system(f'python "{os.path.join(QA_DIR, "reports", "generate_github_summary.py")}"')

    elapsed = time.time() - t_start
    print("\n=================================================================")
    print(f"[OK] COMPLETE QA EXECUTION FINISHED IN {elapsed:.1f}s")
    print(f"Excel Report: {os.path.join(QA_DIR, 'reports', 'E2E_Test_Report_Medinex.xlsx')}")
    print(f"HTML Executive Report: {os.path.join(QA_DIR, 'reports', 'unified_executive_report.html')}")
    print("=================================================================")

if __name__ == "__main__":
    run_all()
