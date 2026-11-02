const fs = require('fs');
const path = require('path');

const png1x1 = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==', 'base64');

fs.mkdirSync('assets', { recursive: true });
['icon.png', 'splash.png', 'adaptive-icon.png', 'favicon.png'].forEach(f => {
  fs.writeFileSync(path.join('assets', f), png1x1);
});
console.log('Created asset icons successfully');
