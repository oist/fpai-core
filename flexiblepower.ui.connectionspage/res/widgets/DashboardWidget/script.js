jsPlumb.ready(function() {
	var prefix = window.location.pathname.search(/console/) > 0 ? "connection-manager/" : "";
	
	var instance = jsPlumb.getInstance({
//		ConnectionOverlays: [
//            [ "Label", { label: "FOO", id: "label", cssClass: "connectionLabel", enabled: false } ]
//		],
		Container: "graph"
	});
	
	instance.registerEndpointType("disabled", { paintStyle: { strokeStyle: "red" } });
	
	var _createTextNode = function(text, isSmall) {
		var node = document.createElement("p");
		if(isSmall) {
			node.className = "small";
		}
		node.innerHTML = text;
		return node;
	}
	
	var _addEndpoint = function(endpoint) {
		// First check if a node with that ID already exists, then just return
		if(document.getElementById(endpoint.id) != null) {
			return;
		}
		
		// Parse the id to split it up into a package, a name and an id
		var pkgName = "", epName = endpoint.id, epId = "";
		
		var data = endpoint.id.match(/^((?:[a-z]+\.)*)([a-zA-Z]+)(?:\.([^\.]+))$/);
		if(data != null) {
			pkgName = data[1];
			epName = data[2];
			epId = data[3];
		} else {
			data = endpoint.id.match(/^((?:[^\.]+\.)*)([^\.]+)$/);
			if(data != null) {
				pkgName = data[1];
				epName = data[2];
				epId = "";
			}
		}
		
		// Now create the node
		var node = $('<div class="node" id="' + endpoint.id + '"><p class="small">' + pkgName + '</p><p>' + epName + '</p><p class="small">' + epId + '</p></div>');
		
		node.bind("mouseenter", function(event){
			$('#properties p, #properties br').detach();
			$('#componentname').html(epName);
			var container = $('#properties');
			
			$('<p>Package: ' + pkgName + '</p>').appendTo(container);
			$('<p>Unique ID: ' + epId + '</p>').appendTo(container);
			$('<br />').appendTo(container);
			for(ix in endpoint.properties) {
				$('<p>' + endpoint.properties[ix] + '</p>').appendTo(container);
			}
			
		});
		
		for(key in endpoint.style) {
			node.css(key, endpoint.style[key]);
		}
		
		// Add the node to the document
		node.appendTo($("#graph"));
		
		// Make it draggable
		instance.draggable(node);
		
		// Now create the ports
		for(ix in endpoint.ports) {
			port = endpoint.ports[ix];
			var ep = instance.addEndpoint(endpoint.id, {
		        endpoint: "Dot",
		        isSource: true,
		        isTarget: true,
		        connector: [ "Bezier", { curviness: 50, gap: 10 }], // [ "Flowchart", { stub: 20, gap: 10, cornerRadius: 5} ],
		        paintStyle: {
		            strokeStyle: "rgb(122,193,3)",
		            fillStyle: "rgb(122,193,3)",
		            radius: 15,
		            lineWidth: 3,
		        },
		        connectorStyle: {
		        	strokeStyle: "rgb(122,193,3)",
		        	lineWidth: 3,
		        },
		        anchor: ["Continuous", { faces:[ "bottom", "top" ] } ],
		        uuid: endpoint.id + ":" + port.id,
		        label: port.id,
		        parameters: { potentialTargets: port.potentialConnections },
		        maxConnections: port.isMultiple ? -1 : 1,
			});
		}
	}
	
	$.ajax(prefix + "currentState", {
		success: function(data) {
			instance.batch(function() {
				for(ix in data.endpoints) {
					_addEndpoint(data.endpoints[ix]);
				}
				
				// Now create the connections that already are active
				for(ix in data.activeConnections) {
					var connection = data.activeConnections[ix];
					
					var match = connection.match(/^(.*:[a-z]*)-(.*:[a-z]*)$/);
					instance.connect({
						source: instance.getEndpoint(match[1]), 
						target: instance.getEndpoint(match[2]),
					});
				}
				
				instance.bind("beforeDrag", function(params) {
					instance.batch(function() {
						instance.selectEndpoints().setEnabled(false);
						instance.selectEndpoints().setType("disabled");
						
						params.endpoint.clearTypes();
						
						var targets = params.endpoint.getParameter("potentialTargets");
						for(ix in targets) {
							var target = instance.getEndpoint(targets[ix]);
							target.setEnabled(true);
							target.clearTypes();
						}
					});
				});
				
				instance.bind("connectionDragStop", function(params) {
					instance.selectEndpoints().setEnabled(true);
					instance.selectEndpoints().each(function(ep) {ep.clearTypes();});
				});
				
				instance.bind("connection", function(params) {
					$.ajax({
						type: "POST",
						contentType: 'application/json',
						processData: false,
						url: prefix + "connect",
						data: JSON.stringify({ source: params.sourceEndpoint.getUuid(), target: params.targetEndpoint.getUuid() }),
					});
				});

				instance.bind("connectionDetached", function(params) {
					if(params.targetEndpoint) {
						$.ajax({
							type: "POST",
							contentType: 'application/json',
							processData: false,
							url: prefix + "disconnect",
							data: JSON.stringify({ source: params.sourceEndpoint.getUuid(), target: params.targetEndpoint.getUuid() }),
						});
					}
				});
			});
		}
	});
	
	$('#autoconnect').click(function() {
		$.ajax(prefix + "autoconnect", {
			success: function(data) {
				window.location.reload();
			}
		});
	});
});