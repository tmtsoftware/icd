// Make the sidebar resizable using jquery-ui resizable
$(document).ready(function () {
    $("#sidebar").resizable({
        handles: 'e',
        minWidth: 150,
        maxWidth: 1200,
        resize: function (event, ui) {
            var x = ui.element.outerWidth();
            var par = $(this).parent().width();
            var ele = ui.element;
            var factor = par - x;

            if (x == par) {
                return;
            }

            $.each(ele.siblings(), function (idx, item) {
                ele.siblings().eq(idx).css('width', (factor) + 'px');
            });

            if (x >= (par - 100)) {
                $("#sidebar").resizable("option", "maxWidth", ui.size.width);
            }
        }
    });
});

//
function addExpander(buttonRef, divRef) {
    $("#demo").on("hide.bs.collapse", function(){
        $(".btn").html('<span class="glyphicon glyphicon-collapse-down"></span> Open');
    });
    $("#demo").on("show.bs.collapse", function(){
        $(".btn").html('<span class="glyphicon glyphicon-collapse-up"></span> Close');
    });
}










