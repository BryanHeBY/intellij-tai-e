import './style.css'
import { build } from './graph'
import { visualize } from './visualization';
import { mockYaml } from './mockdata';


if (process.env.NODE_ENV === 'development') {
    import('./mockdata');
    build(mockYaml, visualize);
} else {
    window.onload = function() {
        window.javaQuery({
            request: JSON.stringify({
                type: 'getFlowGraph',
                data: ''
            }),
            persistent: false,
            onSuccess: function(response: string) {
                build(response, visualize);
            },
            onFailure: function(error_code: number, error_message: string) { }
        })
    };
}