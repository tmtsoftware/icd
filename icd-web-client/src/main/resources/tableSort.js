// Custom sort function for bootstrap-tables
function icdBootstrapTableCustomSort(sortName, sortOrder, data) {
// window.icdBootstrapTableCustomSort = (sortName, sortOrder, data) => {
    // Get the plain text from the HTML string
    function stripHtml(html) {
        var temporalDivElement = document.createElement("p");
        temporalDivElement.innerHTML = html;
        return temporalDivElement.textContent || temporalDivElement.innerText || "";
    }

    const order = sortOrder === 'desc' ? -1 : 1

    data.sort(function (a, b) {
        const aa = stripHtml(a[sortName])
        const bb = stripHtml(b[sortName])

        if (aa < bb) {
            return order * -1
        }
        if (aa > bb) {
            return order
        }
        return 0
    })
}
