var re = React.createElement(App, {data: [0,1,1]});
var html = ReactDOMServer.renderToString(re);
var response = { "content" : html };
response // return