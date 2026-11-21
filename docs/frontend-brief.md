# Frontend brief (specification for the frontend developer)

Written by Boris Plotnikov as the task for the external frontend developer. API: `/swagger-ui.html`.

## Stack and rules

- React 18 + TypeScript, Vite, Tailwind CSS, Recharts. HashRouter, relative URLs, build into `backend/src/main/resources/static`.
- Colours only through CSS variables. CSRF header `X-XSRF-TOKEN` on every change. API errors `{"error"}` shown as they are.
- Amounts: 2 decimals, thousands separator, negative red, positive green, currency after the number.
- Usable at 375 px.

## Screens

| Screen | Content | API |
|---|---|---|
| Login | One-click cards for the demo people with role and office, plus username/password form | `GET /api/auth/demo-users`, `POST /api/auth/login` |
| Demo banner | "Demonstration project…", Take the tour, Reset demo | `POST /api/demo/reset` |
| Dashboard | Profit, turnover, total balance, anomalies + money in transit; daily income/expense chart; accounts; profit by office; top expenses | `GET /api/dashboard` |
| Operations | Quick entry → draft card with warnings → confirm; form; filters; list with corrections nested under the original; Correct dialog | `POST /api/operations/parse`, `POST /api/operations`, `GET /api/operations`, `POST /api/operations/{id}/correct` |
| Accounts | Grouped by office, balance, details with latest operations | `GET /api/accounts`, `GET /api/operations?accountId=` |
| Transfers | Form (currency hint), list with "in transit · X confirms", confirm / reject | `GET/POST /api/transfers`, `POST …/confirm`, `POST …/reject` |
| Shift | Open; blind close form (no expected amounts!); result with expected vs counted after closing; history | `/api/shifts/*` |
| Anomalies | Filter, severity, review / resolve with note | `GET /api/anomalies`, `POST …/decision` |
| Reports | Month select, KPIs, expenses pie, profit by office, CSV export, statement import with check step | `/api/reports/*`, `POST /api/import` |
| Audit log | Paged table | `GET /api/audit` |
| Activity panel | Latest audit entries every 5 s | `GET /api/dashboard` |
| Tour | 7 steps, skips screens the role cannot see | - |

Menu items depend on the role (Shift not for the owner; Anomalies, Reports, Audit only for management), but the API checks permissions anyway.
