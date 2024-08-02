let c = ReactDOMServer.renderToString(
    React.createElement(App, {
            data: [0,1,1]
            })
    );
{ "content" : c } // return context which would be used to templatize the HTML pages


