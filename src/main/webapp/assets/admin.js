const loginView = document.querySelector("#login-view");
const dashboardView = document.querySelector("#dashboard-view");
const dashboardError = document.querySelector("#dashboard-error");
const attendanceRows = document.querySelector("#attendance-rows");
const employeeList = document.querySelector("#employee-list");
const logoutButton = document.querySelector("#logout-button");
const dialog = document.querySelector("#photo-dialog");
let employees = [];

document.querySelector("#dashboard-date").textContent = new Intl.DateTimeFormat(undefined, {
    weekday: "long", month: "long", day: "numeric", year: "numeric"
}).format(new Date());

boot();

async function boot() {
    try {
        const state = await api("api/session");
        if (state.authenticated) {
            showDashboard();
            await loadDashboard();
        } else {
            showLogin();
        }
    } catch (error) {
        showLogin();
        showLoginError(error.message);
    }
}

document.querySelector("#login-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const button = form.querySelector("button[type=submit]");
    button.disabled = true;
    hideLoginError();
    try {
        await api("api/admin/login", { method: "POST", body: new FormData(form) });
        form.reset();
        showDashboard();
        await loadDashboard();
    } catch (error) {
        showLoginError(error.message);
    } finally {
        button.disabled = false;
    }
});

document.querySelector("#toggle-password").addEventListener("click", (event) => {
    const input = document.querySelector("#admin-password");
    const isPassword = input.type === "password";
    input.type = isPassword ? "text" : "password";
    event.currentTarget.textContent = isPassword ? "Hide" : "Show";
    event.currentTarget.setAttribute("aria-label", isPassword ? "Hide password" : "Show password");
});

logoutButton.addEventListener("click", async () => {
    try {
        await api("api/admin/logout", { method: "POST" });
        showLogin();
    } catch (error) {
        showDashboardError(error.message);
    }
});

document.querySelector("#show-employee-form").addEventListener("click", () => {
    const form = document.querySelector("#employee-create-form");
    form.hidden = !form.hidden;
    if (!form.hidden) form.querySelector("input[name=name]").focus();
});

document.querySelector("#cancel-employee-form").addEventListener("click", () => {
    const form = document.querySelector("#employee-create-form");
    form.reset();
    form.hidden = true;
    document.querySelector("#created-employee").hidden = true;
    document.querySelector("#employee-create-error").hidden = true;
});

document.querySelector("#employee-create-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const button = form.querySelector("button[type=submit]");
    button.disabled = true;
    const errorBox = document.querySelector("#employee-create-error");
    errorBox.hidden = true;
    try {
        const employee = await api("api/admin/employees", { method: "POST", body: new FormData(form) });
        const created = document.querySelector("#created-employee");
        created.replaceChildren();
        const message = document.createElement("span");
        message.textContent = `${employee.name} is in. Their employee code is `;
        const code = document.createElement("strong");
        code.textContent = employee.employeeCode;
        const copy = document.createElement("button");
        copy.type = "button";
        copy.className = "text-button copy-code";
        copy.textContent = "Copy code";
        copy.addEventListener("click", async () => {
            try {
                await navigator.clipboard.writeText(employee.employeeCode);
                copy.textContent = "Copied!";
            } catch {
                copy.textContent = employee.employeeCode;
            }
        });
        created.append(message, code, copy);
        created.hidden = false;
        form.reset();
        await loadDashboard();
    } catch (error) {
        errorBox.textContent = error.message;
        errorBox.hidden = false;
    } finally {
        button.disabled = false;
    }
});

document.querySelector("#filters").addEventListener("submit", async (event) => {
    event.preventDefault();
    await refreshAttendance();
});

document.querySelector("#clear-filters").addEventListener("click", async () => {
    document.querySelector("#filters").reset();
    await refreshAttendance();
});

document.querySelector("#refresh-records").addEventListener("click", loadDashboard);
document.querySelector("#close-photo").addEventListener("click", () => dialog.close());
dialog.addEventListener("click", (event) => {
    if (event.target === dialog) dialog.close();
});

async function loadDashboard() {
    clearDashboardError();
    try {
        const [summary] = await Promise.all([
            api("api/admin/summary"),
            loadEmployees(),
            loadAttendance()
        ]);
        document.querySelector("#stat-onsite").textContent = summary.onSiteCount;
        document.querySelector("#stat-punches").textContent = summary.todayCount;
        document.querySelector("#stat-employees").textContent = summary.employeeCount;
    } catch (error) {
        showDashboardError(error.message);
    }
}

async function loadEmployees() {
    employees = await api("api/admin/employees");
    employeeList.replaceChildren();
    const select = document.querySelector("#filter-employee");
    select.replaceChildren(new Option("Everyone", ""));
    for (const employee of employees) {
        const option = new Option(`${employee.name} · ${employee.employeeCode}`, employee.employeeCode);
        select.add(option);
        const item = document.createElement("article");
        item.className = "employee-item";
        const avatar = document.createElement("span");
        avatar.className = "avatar employee-avatar";
        avatar.textContent = employee.name.trim().charAt(0).toUpperCase();
        const info = document.createElement("div");
        info.className = "employee-item-info";
        const name = document.createElement("strong");
        name.textContent = employee.name;
        const department = document.createElement("span");
        department.textContent = employee.department;
        info.append(name, department);
        const code = document.createElement("code");
        code.textContent = employee.employeeCode;
        item.append(avatar, info, code);
        employeeList.append(item);
    }
    if (!employees.length) {
        employeeList.innerHTML = '<p class="empty-team">Your team’s first chapter starts here. Add an employee to get started.</p>';
    }
}

async function loadAttendance() {
    const form = document.querySelector("#filters");
    const query = new URLSearchParams(new FormData(form));
    for (const [key, value] of query.entries()) {
        if (!value) query.delete(key);
    }
    const rows = await api(`api/admin/attendance${query.size ? `?${query}` : ""}`);
    attendanceRows.replaceChildren();
    document.querySelector("#record-count").textContent =
        `${rows.length} ${rows.length === 1 ? "record" : "records"} shown · latest 500`;
    if (!rows.length) {
        attendanceRows.innerHTML = '<tr><td class="empty-state" colspan="5">No attendance records match these filters yet.</td></tr>';
        return;
    }
    for (const record of rows) {
        const row = document.createElement("tr");
        const employeeCell = document.createElement("td");
        const employee = document.createElement("div");
        employee.className = "table-employee";
        const avatar = document.createElement("span");
        avatar.className = "avatar table-avatar";
        avatar.textContent = record.name.trim().charAt(0).toUpperCase();
        const details = document.createElement("span");
        const name = document.createElement("strong");
        name.textContent = record.name;
        const code = document.createElement("small");
        code.textContent = record.employeeCode;
        details.append(name, code);
        employee.append(avatar, details);
        employeeCell.append(employee);
        const departmentCell = textCell(record.department);
        const eventCell = document.createElement("td");
        const badge = document.createElement("span");
        badge.className = `event-badge ${record.eventType === "IN" ? "event-in" : "event-out"}`;
        badge.textContent = record.eventType === "IN" ? "Check in" : "Check out";
        eventCell.append(badge);
        const timeCell = document.createElement("td");
        const time = document.createElement("strong");
        time.className = "record-time";
        const date = document.createElement("small");
        const parsed = new Date(record.recordedAt);
        time.textContent = new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(parsed);
        date.textContent = new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", year: "numeric" }).format(parsed);
        timeCell.append(time, date);
        const photoCell = document.createElement("td");
        const viewButton = document.createElement("button");
        viewButton.className = "photo-button";
        viewButton.type = "button";
        viewButton.textContent = "View photo ↗";
        viewButton.addEventListener("click", () => openPhoto(record));
        photoCell.append(viewButton);
        row.append(employeeCell, departmentCell, eventCell, timeCell, photoCell);
        attendanceRows.append(row);
    }
}

async function refreshAttendance() {
    clearDashboardError();
    try {
        await loadAttendance();
    } catch (error) {
        showDashboardError(error.message);
    }
}

function openPhoto(record) {
    document.querySelector("#photo-caption").textContent =
        `${record.name} · ${record.eventType === "IN" ? "Check in" : "Check out"}`;
    const photo = document.querySelector("#full-photo");
    photo.src = `api/admin/photo?id=${encodeURIComponent(record.id)}`;
    dialog.showModal();
}

function textCell(value) {
    const cell = document.createElement("td");
    cell.textContent = value || "—";
    return cell;
}

function showDashboard() {
    loginView.hidden = true;
    dashboardView.hidden = false;
    logoutButton.hidden = false;
}

function showLogin() {
    dashboardView.hidden = true;
    loginView.hidden = false;
    logoutButton.hidden = true;
    document.querySelector("#admin-password").focus();
}

function showLoginError(message) {
    const box = document.querySelector("#login-error");
    box.textContent = message;
    box.hidden = false;
}

function hideLoginError() {
    const box = document.querySelector("#login-error");
    box.textContent = "";
    box.hidden = true;
}

function showDashboardError(message) {
    dashboardError.textContent = message;
    dashboardError.hidden = false;
}

function clearDashboardError() {
    dashboardError.textContent = "";
    dashboardError.hidden = true;
}

async function api(url, options = {}) {
    let response;
    try {
        response = await fetch(url, { ...options, headers: { Accept: "application/json", ...options.headers } });
    } catch {
        throw new Error("Couldn’t reach Daymark. Check the connection and try again.");
    }
    let data;
    try {
        data = await response.json();
    } catch {
        throw new Error("The server returned an unexpected response. Please try again.");
    }
    if (!response.ok) throw new Error(data.error || "Something went wrong. Please try again.");
    return data;
}
