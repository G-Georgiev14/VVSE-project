const sky = document.getElementById("sky");

function createCloud(startPosition) {
  const cloud = document.createElement("div");
  cloud.classList.add("cloud");

  const rows = 3 + Math.floor(Math.random() * 2); // VERY FLAT
  const cols = 7 + Math.floor(Math.random() * 5); // Wide

  cloud.style.gridTemplateColumns = `repeat(${cols}, 1.125rem)`;

  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {

      const pixel = document.createElement("div");
      pixel.classList.add("pixel");

      // Create horizontal dominant shape
      const edgeFade = 1;

      if (
        c < edgeFade && Math.random() > 0.6 ||
        c > cols - edgeFade && Math.random() > 0.6 ||
        r === 0 && Math.random() > 0.8 ||
        r === rows - 1 && Math.random() > 0.8
      ) {
        pixel.style.visibility = "hidden";
      }

      cloud.appendChild(pixel);
    }
  }

  cloud.style.opacity = (0.6 + Math.random()) * 0.67;

  sky.appendChild(cloud);

  // Only spawn in upper sky
  cloud.style.top = Math.random() * (window.innerHeight * 0.8) + "px";

  const duration = 25000 + Math.random() * 15000;

  if (startPosition === "left") {
    cloud.style.left = "-37.5rem";
  } else {
    cloud.style.left = "40vw";
  }

  cloud.animate(
    [
      { transform: "translateX(0)" },
      { transform: "translateX(120vw)" }
    ],
    {
      duration: duration,
      iterations: 1,
      easing: "linear"
    }
  );

  setTimeout(() => cloud.remove(), duration);
}

setInterval(() => createCloud("left"), 3000);
setInterval(() => createCloud("middle"), 4000);
