// index.js
import '@stemplayer-js/stemplayer-js/element.js'

// Dummy data and functionality
const data = {
    name: "Dummy Project",
    version: "1.0.0",
};

// Function to display dummy data
function displayData() {
    console.log("Project Name:", data.name);
    console.log("Version:", data.version);
}

// Call the function to test
displayData();

// Export the data and function for use in other files
export { data, displayData };
