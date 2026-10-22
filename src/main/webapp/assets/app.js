const employeeForm = document.querySelector("#employee-form");
const employeeCode = document.querySelector("#employee-code");
const employeeConfirmation = document.querySelector("#employee-confirmation");
const cameraArea = document.querySelector("#camera-area");
const cameraPreview = document.querySelector("#camera-preview");
const photoPreview = document.querySelector("#photo-preview");
const cameraPlaceholder = document.querySelector("#camera-placeholder");
const cameraStatus = document.querySelector("#camera-status");
const startCameraButton = document.querySelector("#start-camera");
const takePhotoButton = document.querySelector("#take-photo");
const retakePhotoButton = document.querySelector("#retake-photo");
const choosePhotoButton = document.querySelector("#choose-photo");
const photoUpload = document.querySelector("#photo-upload");
const submitAttendanceButton = document.querySelector("#submit-attendance");
const errorMessage = document.querySelector("#form-error");
let employee = null;
let capturedPhoto = null;
let cameraStream = null;

document.querySelector("#today-label").textContent = new Intl.DateTimeFormat(undefined, {
    weekday: "short", month: "short", day: "numeric"
}).format(new Date());
document.querySelector("#footer-year").textContent = new Date().getFullYear();

employeeForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    hideError();
    const code = employeeCode.value.trim().toUpperCase();
    if (!code) {
        showError("Enter your employee code to continue.");
        return;
    }
    const button = employeeForm.querySelector("button[type=submit]");
    button.disabled = true;
    try {
        const response = await fetch(`api/employee?code=${encodeURIComponent(code)}`, {
            headers: { Accept: "application/json" }
        });
        const data = await readResponse(response);
        employee = data;
        document.querySelector("#employee-name").textContent = "Employee code confirmed";
        document.querySelector("#employee-department").textContent = "Ready for a quick photo";
        document.querySelector("#employee-avatar").textContent = "✓";
        employeeForm.hidden = true;
        employeeConfirmation.hidden = false;
        cameraArea.hidden = false;
        document.querySelector("#clock-title").textContent = "Looking good. Let’s take a photo.";
        cameraArea.scrollIntoView({ behavior: "smooth", block: "nearest" });
    } catch (error) {
        showError(error.message);
    } finally {
        button.disabled = false;
    }
});

startCameraButton.addEventListener("click", startCamera);

async function startCamera() {
    hideError();
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        showPhotoFallback("Camera access isn’t available here. Choose a photo instead.");
        return;
    }
    stopCamera();
    try {
        cameraStream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 960 } },
            audio: false
        });
        cameraPreview.srcObject = cameraStream;
        cameraPreview.hidden = false;
        photoPreview.hidden = true;
        cameraPlaceholder.hidden = true;
        cameraStatus.textContent = "CAMERA ON";
        takePhotoButton.disabled = false;
        takePhotoButton.hidden = false;
        retakePhotoButton.hidden = true;
        startCameraButton.textContent = "Camera ready";
        startCameraButton.disabled = true;
        if (choosePhotoButton) choosePhotoButton.hidden = true;
    } catch (error) {
        showPhotoFallback(error.name === "NotAllowedError"
            ? "Camera permission was blocked. Allow access or choose a photo instead."
            : "We couldn’t start your camera. Choose a photo instead.");
    }
}

takePhotoButton.addEventListener("click", () => {
    if (!cameraPreview.videoWidth || !cameraStream) {
        showError("The camera is still starting. Give it a moment and try again.");
        return;
    }
    const canvas = document.createElement("canvas");
    canvas.width = cameraPreview.videoWidth;
    canvas.height = cameraPreview.videoHeight;
    const context = canvas.getContext("2d");
    if (!context) {
        showError("Your browser couldn’t prepare that photo. Please try again.");
        return;
    }
    context.translate(canvas.width, 0);
    context.scale(-1, 1);
    context.drawImage(cameraPreview, 0, 0, canvas.width, canvas.height);
    canvas.toBlob((blob) => {
        if (!blob) {
            showError("Your browser couldn’t save that photo. Please try again.");
            return;
        }
        setCapturedPhoto(blob);
    }, "image/jpeg", 0.88);
});

retakePhotoButton.addEventListener("click", async () => {
    clearCapturedPhoto();
    if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
        startCameraButton.hidden = false;
        startCameraButton.disabled = false;
        startCameraButton.textContent = "Start camera";
        await startCamera();
    } else {
        photoUpload.click();
    }
});

if (choosePhotoButton) {
    choosePhotoButton.addEventListener("click", () => photoUpload.click());
}

photoUpload.addEventListener("change", () => {
    const file = photoUpload.files && photoUpload.files[0];
    if (!file) return;
    if (!file.type.startsWith("image/")) {
        showError("Choose an image file to continue.");
        photoUpload.value = "";
        return;
    }
    const image = new Image();
    const previewUrl = URL.createObjectURL(file);
    image.onload = () => {
        const maxDimension = 1600;
        const scale = Math.min(1, maxDimension / Math.max(image.width, image.height));
        const canvas = document.createElement("canvas");
        canvas.width = Math.round(image.width * scale);
        canvas.height = Math.round(image.height * scale);
        const context = canvas.getContext("2d");
        if (!context) {
            URL.revokeObjectURL(previewUrl);
            showError("Your browser couldn’t prepare that photo. Please try again.");
            return;
        }
        context.drawImage(image, 0, 0, canvas.width, canvas.height);
        canvas.toBlob((blob) => {
            URL.revokeObjectURL(previewUrl);
            if (!blob) {
                showError("Your browser couldn’t save that photo. Please try again.");
                return;
            }
            setCapturedPhoto(blob);
        }, "image/jpeg", 0.88);
    };
    image.onerror = () => {
        URL.revokeObjectURL(previewUrl);
        showError("We couldn’t open that image. Choose another photo.");
    };
    image.src = previewUrl;
});

submitAttendanceButton.addEventListener("click", async () => {
    if (!employee || !capturedPhoto) {
        showError("Please take a photo before recording attendance.");
        return;
    }
    hideError();
    submitAttendanceButton.disabled = true;
    document.querySelector("#submit-label").textContent = "Saving your attendance…";
    try {
        const response = await fetch("api/attendance", {
            method: "POST",
            headers: { "Content-Type": "image/jpeg", "X-Employee-Code": employee.employeeCode },
            body: capturedPhoto
        });
        const data = await readResponse(response);
        stopCamera();
        cameraArea.hidden = true;
        employeeConfirmation.hidden = true;
        document.querySelector("#clock-title").textContent = "That’s your day, marked.";
        const isCheckIn = data.eventType === "IN";
        document.querySelector("#success-title").textContent =
            isCheckIn ? `Good to have you here, ${data.employeeName.split(" ")[0]}.` : "Have a lovely rest of your day.";
        const recordedAt = new Date(`${data.recordedAt}`);
        const time = new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(recordedAt);
        document.querySelector("#success-message").textContent =
            `${isCheckIn ? "Check-in" : "Check-out"} recorded at ${time}.`;
        document.querySelector("#success-panel").hidden = false;
    } catch (error) {
        showError(error.message);
        submitAttendanceButton.disabled = false;
        document.querySelector("#submit-label").textContent = "Record attendance";
    }
});

document.querySelector("#change-employee").addEventListener("click", resetCheckIn);
document.querySelector("#new-checkin").addEventListener("click", resetCheckIn);

function setCapturedPhoto(blob) {
    clearCapturedPhoto();
    capturedPhoto = blob;
    photoPreview.src = URL.createObjectURL(blob);
    photoPreview.hidden = false;
    cameraPreview.hidden = true;
    cameraPlaceholder.hidden = true;
    cameraStatus.textContent = "PHOTO READY";
    stopCamera();
    takePhotoButton.hidden = true;
    takePhotoButton.disabled = true;
    startCameraButton.hidden = true;
    if (choosePhotoButton) choosePhotoButton.hidden = true;
    retakePhotoButton.hidden = false;
    submitAttendanceButton.disabled = false;
    hideError();
}

function clearCapturedPhoto() {
    if (photoPreview.src.startsWith("blob:")) URL.revokeObjectURL(photoPreview.src);
    photoPreview.removeAttribute("src");
    photoPreview.hidden = true;
    capturedPhoto = null;
    submitAttendanceButton.disabled = true;
    document.querySelector("#submit-label").textContent = "Record attendance";
}

function showPhotoFallback(message) {
    showError(message);
    if (choosePhotoButton) choosePhotoButton.hidden = false;
    takePhotoButton.hidden = true;
    takePhotoButton.disabled = true;
    startCameraButton.disabled = false;
    startCameraButton.textContent = "Try camera again";
    cameraStatus.textContent = "PHOTO NEEDED";
}

function stopCamera() {
    if (cameraStream) {
        cameraStream.getTracks().forEach((track) => track.stop());
        cameraStream = null;
    }
    cameraPreview.srcObject = null;
}

function resetCheckIn() {
    stopCamera();
    clearCapturedPhoto();
    employee = null;
    employeeCode.value = "";
    photoUpload.value = "";
    document.querySelector("#success-panel").hidden = true;
    document.querySelector("#employee-confirmation").hidden = true;
    document.querySelector("#camera-area").hidden = true;
    document.querySelector("#employee-form").hidden = false;
    document.querySelector("#clock-title").textContent = "Let’s get you on the record.";
    startCameraButton.hidden = false;
    startCameraButton.disabled = false;
    startCameraButton.textContent = "Start camera";
    takePhotoButton.hidden = false;
    takePhotoButton.disabled = true;
    retakePhotoButton.hidden = true;
    if (choosePhotoButton) choosePhotoButton.hidden = true;
    cameraPlaceholder.hidden = false;
    cameraStatus.textContent = "CAMERA READY";
    submitAttendanceButton.disabled = true;
    hideError();
    window.scrollTo({ top: 0, behavior: "smooth" });
}

function showError(message) {
    errorMessage.textContent = message;
    errorMessage.hidden = false;
}

function hideError() {
    errorMessage.textContent = "";
    errorMessage.hidden = true;
}

async function readResponse(response) {
    let data;
    try {
        data = await response.json();
    } catch {
        if (response.status >= 500) {
            throw new Error(`The attendance server returned an error (${response.status}). Check the MySQL setup and try again.`);
        }
        throw new Error(`The server returned an unexpected response (${response.status}). Please try again.`);
    }
    if (!response.ok) throw new Error(data.error || "Something went wrong. Please try again.");
    return data;
}

window.addEventListener("pagehide", stopCamera);
